package com.sentinelmine.repository;

import com.sentinelmine.entity.FaltaEPP;
import com.sentinelmine.entity.enums.EstadoAlerta;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FaltaEPPRepository extends JpaRepository<FaltaEPP, Long> {

    List<FaltaEPP> findByEventoTurno_EventoId(Long eventoId);

    List<FaltaEPP> findByEstadoAlertaOrderByFechaHoraDesc(EstadoAlerta estadoAlerta);

    @Query("SELECT f FROM FaltaEPP f " +
           "JOIN FETCH f.epp " +
           "JOIN FETCH f.eventoTurno e " +
           "JOIN FETCH e.turno " +
           "LEFT JOIN FETCH f.rol " +
           "ORDER BY f.fechaHora DESC")
    List<FaltaEPP> findUltimasFaltas(Pageable pageable);

    @Query("SELECT f FROM FaltaEPP f " +
           "JOIN FETCH f.epp " +
           "JOIN FETCH f.eventoTurno e " +
           "WHERE f.estadoAlerta = :estado " +
           "ORDER BY f.fechaHora DESC")
    List<FaltaEPP> findByEstadoWithDetails(@Param("estado") EstadoAlerta estado);
}
