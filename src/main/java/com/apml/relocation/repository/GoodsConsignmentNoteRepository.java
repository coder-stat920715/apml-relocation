package com.apml.relocation.repository;

import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.GoodsConsignmentNote;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoodsConsignmentNoteRepository extends JpaRepository<GoodsConsignmentNote, Long> {
    Optional<GoodsConsignmentNote> findByConsignment(Consignment consignment);
    boolean existsByGcnSerialNo(Long gcnSerialNo);
    Optional<GoodsConsignmentNote> findTopByOrderByGcnSerialNoDesc();
    Optional<GoodsConsignmentNote> findByGcnSerialNo(Long gcnSerialNo);
    Optional<GoodsConsignmentNote> findByDigitalGcNo(String digitalGcNo);
    boolean existsByDigitalGcNo(String digitalGcNo);
    Optional<GoodsConsignmentNote> findTopByDigitalGcNoStartingWithOrderByDigitalGcNoDesc(String prefix);
}
