package com.sentinelmine.repository;

import com.sentinelmine.entity.CierreAuditoriaTurno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CierreAuditoriaTurnoRepository extends JpaRepository<CierreAuditoriaTurno, Long> {

    Optional<CierreAuditoriaTurno> findByEventoTurno_EventoId(Long eventoId);

    boolean existsByEventoTurno_EventoId(Long eventoId);

    @Query("SELECT c FROM CierreAuditoriaTurno c " +
           "JOIN FETCH c.eventoTurno e " +
           "JOIN FETCH e.turno " +
           "WHERE c.eventoTurno.eventoId = :eventoId")
    Optional<CierreAuditoriaTurno> findByEventoIdWithDetails(@Param("eventoId") Long eventoId);

    @Query("SELECT c FROM CierreAuditoriaTurno c " +
           "JOIN FETCH c.eventoTurno e " +
           "JOIN FETCH e.turno " +
           "ORDER BY c.fechaHora DESC")
    List<CierreAuditoriaTurno> findAllOrderByFechaHoraDesc();
}
