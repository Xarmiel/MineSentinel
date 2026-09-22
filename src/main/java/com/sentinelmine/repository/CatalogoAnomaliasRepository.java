package com.sentinelmine.repository;

import com.sentinelmine.entity.CatalogoAnomalias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CatalogoAnomaliasRepository extends JpaRepository<CatalogoAnomalias, Long> {
    Optional<CatalogoAnomalias> findByNombreIgnoreCase(String nombre);
}
