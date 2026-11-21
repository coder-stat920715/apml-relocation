package com.apml.relocation.repository;

import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.MoneyReceipt;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MoneyReceiptRepository extends JpaRepository<MoneyReceipt, Long> {
    List<MoneyReceipt> findByConsignmentOrderByReceivedAtAsc(Consignment consignment);
    boolean existsByReceiptNo(String receiptNo);
}
