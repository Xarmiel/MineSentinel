package com.sentinelmine.repository;

import com.sentinelmine.entity.EventoTurno;
import com.sentinelmine.entity.enums.EstadoTurno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventoTurnoRepository extends JpaRepository<EventoTurno, Long> {

    /**
     * Recupera todos los turnos con un estado específico.
     * Soporta consultar múltiples turnos en estado 'ACTIVO' simultáneamente durante el solapamiento de turnos.
     */
    @Query("SELECT e FROM EventoTurno e JOIN FETCH e.turno WHERE e.estado = :estado ORDER BY e.fechaInicio DESC")
    List<EventoTurno> findAllByEstadoWithTurno(@Param("estado") EstadoTurno estado);

    List<EventoTurno> findByEstado(EstadoTurno estado);

    @Query("SELECT e FROM EventoTurno e JOIN FETCH e.turno WHERE e.eventoId = :eventoId")
    Optional<EventoTurno> findByIdWithTurno(@Param("eventoId") Long eventoId);

    Optional<EventoTurno> findFirstByTurno_TurnoIdAndEstado(Long turnoId, EstadoTurno estado);

    boolean existsByTurno_TurnoIdAndEstado(Long turnoId, EstadoTurno estado);
}
