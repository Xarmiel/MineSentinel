package com.sentinelmine.repository;

import com.sentinelmine.entity.CatalogoEPP;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CatalogoEPPRepository extends JpaRepository<CatalogoEPP, Long> {
    Optional<CatalogoEPP> findByNombreIgnoreCase(String nombre);
}
