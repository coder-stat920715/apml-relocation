package com.apml.relocation.repository;

import com.apml.relocation.domain.model.Enquiry;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnquiryRepository extends JpaRepository<Enquiry, Long> {
    Optional<Enquiry> findByEnquiryNo(String enquiryNo);
    boolean existsByEnquiryNo(String enquiryNo);
}
