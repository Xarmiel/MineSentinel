package com.sentinelmine.repository;

import com.sentinelmine.entity.ConfiguracionAforo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfiguracionAforoRepository extends JpaRepository<ConfiguracionAforo, Long> {
}
