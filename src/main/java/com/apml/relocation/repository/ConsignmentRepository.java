package com.apml.relocation.repository;

import com.apml.relocation.domain.model.Consignment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsignmentRepository extends JpaRepository<Consignment, Long> {
    Optional<Consignment> findByCfrNo(String cfrNo);
    boolean existsByCfrNo(String cfrNo);
}
