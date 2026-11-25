package com.apml.relocation.service;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.enums.CoordinatorRole;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.CoordinatorContact;
import com.apml.relocation.exception.BusinessRuleException;
import com.apml.relocation.exception.ResourceNotFoundException;
import com.apml.relocation.repository.ConsignmentRepository;
import com.apml.relocation.repository.CoordinatorContactRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CoordinatorService {

    private final CoordinatorContactRepository contacts;
    private final ConsignmentRepository consignments;
    private final Clock clock;

    /** Adds a contact, or updates the one with the same role and name (a role may have several people). */
    @Transactional
    public CoordinatorContact assign(String cfrNo, CoordinatorRole role, String name, String branchCode, String phoneNumber) {
        Consignment c = load(cfrNo);
        if (c.getStatus() == ConsignmentStatus.CANCELLED || c.getStatus() == ConsignmentStatus.CLOSED) {
            throw new BusinessRuleException("Coordinators cannot be changed on a " + c.getStatus() + " consignment");
        }
        String cleanName = name.trim();
        CoordinatorContact contact = contacts.findByConsignmentOrderByAssignedAtAscIdAsc(c).stream()
                .filter(x -> x.getRole() == role && x.getName().equalsIgnoreCase(cleanName))
                .findFirst()
                .orElseGet(CoordinatorContact::new);
        contact.setConsignment(c);
        contact.setRole(role);
        contact.setName(cleanName);
        contact.setBranchCode(branchCode.trim().toUpperCase());
        contact.setPhoneNumber(phoneNumber);
        contact.setAssignedAt(LocalDateTime.now(clock));
        return contacts.save(contact);
    }

    @Transactional(readOnly = true)
    public List<CoordinatorContact> list(String cfrNo) {
        return list(load(cfrNo));
    }

    @Transactional(readOnly = true)
    public List<CoordinatorContact> list(Consignment c) {
        return contacts.findByConsignmentOrderByAssignedAtAscIdAsc(c);
    }

    private Consignment load(String cfrNo) {
        return consignments.findByCfrNo(cfrNo).orElseThrow(() -> new ResourceNotFoundException("Consignment", cfrNo));
    }
}
