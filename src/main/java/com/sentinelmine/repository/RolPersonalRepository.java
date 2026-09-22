package com.sentinelmine.repository;

import com.sentinelmine.entity.RolPersonal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RolPersonalRepository extends JpaRepository<RolPersonal, Long> {
    Optional<RolPersonal> findByNombreIgnoreCase(String nombre);
    Optional<RolPersonal> findByColorCascoIgnoreCase(String colorCasco);
}
