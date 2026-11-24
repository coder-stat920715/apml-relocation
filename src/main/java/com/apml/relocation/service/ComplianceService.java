package com.apml.relocation.service;

import com.apml.relocation.domain.enums.ItemCategory;
import com.apml.relocation.domain.enums.KycType;
import com.apml.relocation.domain.enums.RestrictedItem;
import com.apml.relocation.domain.model.ArticleLine;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.KycDocument;
import com.apml.relocation.domain.model.MasterArticleDeclaration;
import com.apml.relocation.domain.model.SurveyItem;
import com.apml.relocation.domain.model.VehicleChecklist;
import com.apml.relocation.exception.ComplianceViolationException;
import com.apml.relocation.exception.ResourceNotFoundException;
import com.apml.relocation.repository.ConsignmentRepository;
import com.apml.relocation.repository.KycDocumentRepository;
import com.apml.relocation.repository.MasterArticleDeclarationRepository;
import com.apml.relocation.repository.VehicleChecklistRepository;
import com.apml.relocation.util.MaskingUtil;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceService {

    static final BigDecimal MAX_CAR_FUEL_LITERS = new BigDecimal("24.0");
    private static final Pattern AADHAAR = Pattern.compile("^\\d{12}$");
    private static final Pattern PAN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");

    private final ConsignmentRepository consignments;
    private final KycDocumentRepository kycDocuments;
    private final VehicleChecklistRepository checklists;
    private final MasterArticleDeclarationRepository declarations;
    private final Clock clock;

    // ---------------------------------------------------------------- vehicle checklist

    /** Auto-triggered at quotation time when the consignment carries a two- or four-wheeler. */
    @Transactional
    public void initVehicleChecklists(Consignment c) {
        for (SurveyItem item : c.getEnquiry().getItems()) {
            if (item.getCategory().isVehicle()) {
                VehicleChecklist v = new VehicleChecklist();
                v.setConsignment(c);
                v.setSurveyItem(item);
                checklists.save(v);
            }
        }
    }

    @Transactional
    public VehicleChecklist updateVehicleChecklist(String cfrNo, Long surveyItemId, boolean rcBook, boolean validInsurance,
                                                   boolean pucCertificate, boolean roadTaxReceipt,
                                                   Boolean fuelTankEmpty, BigDecimal fuelLiters) {
        Consignment c = load(cfrNo);
        assertEditable(c);
        VehicleChecklist v = checklists.findByConsignmentAndSurveyItem_Id(c, surveyItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle checklist for survey item", String.valueOf(surveyItemId)));
        v.setRcBook(rcBook);
        v.setValidInsurance(validInsurance);
        v.setPucCertificate(pucCertificate);
        v.setRoadTaxReceipt(roadTaxReceipt);
        v.setFuelTankEmpty(fuelTankEmpty);
        v.setFuelLiters(fuelLiters);
        fuelViolation(v).ifPresent(msg -> { throw new ComplianceViolationException(List.of(msg)); });
        return checklists.save(v);
    }

    @Transactional(readOnly = true)
    public List<VehicleChecklist> listVehicleChecklists(String cfrNo) {
        return checklists.findByConsignment(load(cfrNo));
    }

    /** Two-wheeler: tank must be empty. Four-wheeler: at most 24.0 L. */
    static Optional<String> fuelViolation(VehicleChecklist v) {
        ItemCategory cat = v.getSurveyItem().getCategory();
        String label = v.getSurveyItem().getDescription();
        if (cat == ItemCategory.TWO_WHEELER && !Boolean.TRUE.equals(v.getFuelTankEmpty())) {
            return Optional.of(label + ": fuel tank must be drained empty before loading (two-wheeler)");
        }
        if (cat == ItemCategory.FOUR_WHEELER) {
            if (v.getFuelLiters() == null) {
                return Optional.of(label + ": fuel level must be recorded (four-wheeler)");
            }
            if (v.getFuelLiters().compareTo(MAX_CAR_FUEL_LITERS) > 0) {
                return Optional.of(label + ": fuel must not exceed 24.0 litres (found " + v.getFuelLiters() + ")");
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- KYC

    @Transactional
    public KycDocument submitKyc(String cfrNo, KycType type, String number, Integer photoCount) {
        Consignment c = load(cfrNo);
        assertEditable(c);
        String normalized = number == null ? null : number.replaceAll("\\s", "").toUpperCase();
        if (type == KycType.AADHAAR) {
            require(normalized != null && AADHAAR.matcher(normalized).matches(), "Aadhaar must be a 12-digit number");
        } else if (type == KycType.PAN) {
            require(normalized != null && PAN.matcher(normalized).matches(), "PAN must match format ABCDE1234F");
        } else {
            int needed = requiredPhotos(c);
            require(photoCount != null && photoCount >= needed, "At least " + needed + " passport photos are required");
        }
        KycDocument d = kycDocuments.findByConsignmentAndType(c, type).orElseGet(KycDocument::new);
        d.setConsignment(c);
        d.setType(type);
        d.setDocumentNumber(type == KycType.PASSPORT_PHOTOS ? null : normalized);
        d.setPhotoCount(type == KycType.PASSPORT_PHOTOS ? photoCount : null);
        d.setVerified(true);
        log.info("KYC {} recorded for {} (id {})", type, cfrNo, MaskingUtil.mask(normalized));
        return kycDocuments.save(d);
    }

    @Transactional(readOnly = true)
    public List<KycDocument> listKyc(String cfrNo) {
        return kycDocuments.findByConsignment(load(cfrNo));
    }

    /** 2 photos for household goods / bike, 4 if a car is included. */
    static int requiredPhotos(Consignment c) {
        return c.getEnquiry().hasCategory(ItemCategory.FOUR_WHEELER) ? 4 : 2;
    }

    // ---------------------------------------------------------------- Master Article List

    @Transactional
    public MasterArticleDeclaration submitMasterArticle(String cfrNo, String signedBy, Set<RestrictedItem> restricted,
                                                        List<ArticleLine> submitted) {
        Consignment c = load(cfrNo);
        assertEditable(c);
        if (restricted != null && !restricted.isEmpty()) {
            throw new ComplianceViolationException(List.of(
                    "Restricted items present " + restricted + ": Master Article List cannot be accepted"));
        }
        if (LocalDateTime.now(clock).plusHours(24).isAfter(c.getPackingDate().atStartOfDay())) {
            throw new ComplianceViolationException(List.of(
                    "Master Article List must be submitted at least 24 hours before packing (packing date "
                            + c.getPackingDate() + ")"));
        }
        Map<Long, SurveyItem> items = new LinkedHashMap<>();
        c.getEnquiry().getItems().forEach(i -> items.put(i.getId(), i));

        List<String> errors = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (ArticleLine l : submitted) {
            if (!items.containsKey(l.getSurveyItemId())) errors.add("Unknown survey item " + l.getSurveyItemId());
            else if (!seen.add(l.getSurveyItemId())) errors.add("Duplicate line for survey item " + l.getSurveyItemId());
            if (l.getDeclaredValue().signum() < 0 || l.getRiskCoverageAvailed().signum() < 0) {
                errors.add("Negative amounts are not allowed (item " + l.getSurveyItemId() + ")");
            }
            if (l.getRiskCoverageAvailed().compareTo(l.getDeclaredValue()) > 0) {
                errors.add("Risk coverage cannot exceed declared value (item " + l.getSurveyItemId() + ")");
            }
        }
        if (!errors.isEmpty()) throw new ComplianceViolationException(errors);

        List<ArticleLine> finalLines = new ArrayList<>(submitted);
        // Omitted items default to zero declared value and are ineligible for damage claims.
        items.keySet().stream().filter(id -> !seen.contains(id))
                .forEach(id -> finalLines.add(new ArticleLine(id, BigDecimal.ZERO, BigDecimal.ZERO)));

        MasterArticleDeclaration d = declarations.findByConsignment(c).orElseGet(MasterArticleDeclaration::new);
        d.setConsignment(c);
        d.setSignedBy(signedBy);
        d.setSignedAt(LocalDateTime.now(clock));
        d.getLines().clear();
        d.getLines().addAll(finalLines);
        return declarations.save(d);
    }

    @Transactional(readOnly = true)
    public MasterArticleDeclaration getMasterArticle(String cfrNo) {
        return declarations.findByConsignment(load(cfrNo))
                .orElseThrow(() -> new ResourceNotFoundException("Master Article List for", cfrNo));
    }

    // ---------------------------------------------------------------- aggregate gate

    @Transactional(readOnly = true)
    public List<String> evaluate(Consignment c) {
        List<String> v = new ArrayList<>();

        Map<KycType, KycDocument> kyc = new LinkedHashMap<>();
        kycDocuments.findByConsignment(c).forEach(d -> kyc.put(d.getType(), d));
        for (KycType t : KycType.values()) {
            KycDocument d = kyc.get(t);
            if (d == null || !d.isVerified()) {
                v.add("KYC document missing or unverified: " + t);
            } else if (t == KycType.PASSPORT_PHOTOS && (d.getPhotoCount() == null || d.getPhotoCount() < requiredPhotos(c))) {
                v.add("Passport photos: " + requiredPhotos(c) + " required");
            }
        }

        for (VehicleChecklist vc : checklists.findByConsignment(c)) {
            String n = vc.getSurveyItem().getDescription();
            if (!vc.isRcBook()) v.add(n + ": RC_BOOK missing");
            if (!vc.isValidInsurance()) v.add(n + ": VALID_INSURANCE missing");
            if (!vc.isPucCertificate()) v.add(n + ": PUC_CERTIFICATE missing");
            if (!vc.isRoadTaxReceipt()) v.add(n + ": ROAD_TAX_RECEIPT missing");
            fuelViolation(vc).ifPresent(v::add);
        }

        if (declarations.findByConsignment(c).isEmpty()) {
            v.add("Signed Master Article List (valuation declaration) not submitted");
        }
        return v;
    }

    @Transactional(readOnly = true)
    public List<String> evaluate(String cfrNo) {
        return evaluate(load(cfrNo));
    }

    @Transactional(readOnly = true)
    public void assertCompliant(Consignment c) {
        List<String> violations = evaluate(c);
        if (!violations.isEmpty()) throw new ComplianceViolationException(violations);
    }

    // ---------------------------------------------------------------- helpers

    private Consignment load(String cfrNo) {
        return consignments.findByCfrNo(cfrNo).orElseThrow(() -> new ResourceNotFoundException("Consignment", cfrNo));
    }

    private static void assertEditable(Consignment c) {
        if (!c.getStatus().isDocumentEditable()) {
            throw new ComplianceViolationException(List.of("Documents can no longer be changed in status " + c.getStatus()));
        }
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new ComplianceViolationException(List.of(message));
    }
}
