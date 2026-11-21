package com.apml.relocation.repository;

import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.DamageClaim;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DamageClaimRepository extends JpaRepository<DamageClaim, Long> {
    List<DamageClaim> findByConsignment(Consignment consignment);
}
