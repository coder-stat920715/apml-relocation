package com.apml.relocation.repository;

import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.MasterArticleDeclaration;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MasterArticleDeclarationRepository extends JpaRepository<MasterArticleDeclaration, Long> {
    Optional<MasterArticleDeclaration> findByConsignment(Consignment consignment);
}
