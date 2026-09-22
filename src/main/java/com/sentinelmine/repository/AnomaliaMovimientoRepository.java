package com.sentinelmine.repository;

import com.sentinelmine.entity.AnomaliaMovimiento;
import com.sentinelmine.entity.enums.EstadoAlerta;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnomaliaMovimientoRepository extends JpaRepository<AnomaliaMovimiento, Long> {

    List<AnomaliaMovimiento> findByEventoTurno_EventoId(Long eventoId);

    List<AnomaliaMovimiento> findByEstadoAlertaOrderByFechaHoraDesc(EstadoAlerta estadoAlerta);

    @Query("SELECT a FROM AnomaliaMovimiento a " +
           "JOIN FETCH a.catalogoAnomalia " +
           "JOIN FETCH a.eventoTurno e " +
           "JOIN FETCH e.turno " +
           "LEFT JOIN FETCH a.rol " +
           "ORDER BY a.fechaHora DESC")
    List<AnomaliaMovimiento> findUltimasAnomalias(Pageable pageable);

    @Query("SELECT a FROM AnomaliaMovimiento a " +
           "JOIN FETCH a.catalogoAnomalia " +
           "JOIN FETCH a.eventoTurno e " +
           "WHERE a.estadoAlerta = :estado " +
           "ORDER BY a.fechaHora DESC")
    List<AnomaliaMovimiento> findByEstadoWithDetails(@Param("estado") EstadoAlerta estado);
}
