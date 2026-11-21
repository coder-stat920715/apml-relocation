package com.apml.relocation.repository;

import com.apml.relocation.domain.enums.KycType;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.KycDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KycDocumentRepository extends JpaRepository<KycDocument, Long> {
    List<KycDocument> findByConsignment(Consignment consignment);
    Optional<KycDocument> findByConsignmentAndType(Consignment consignment, KycType type);
}
