package com.sentinelmine.repository;

import com.sentinelmine.entity.MovimientoAforo;
import com.sentinelmine.entity.enums.TipoMovimiento;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovimientoAforoRepository extends JpaRepository<MovimientoAforo, Long> {

    long countByEventoTurno_EventoIdAndTipoMovimiento(Long eventoId, TipoMovimiento tipoMovimiento);

    long countByEventoTurno_EventoIdAndTipoMovimientoAndRol_RequiereAforoTrue(Long eventoId, TipoMovimiento tipoMovimiento);

    @Query("SELECT COUNT(m) FROM MovimientoAforo m " +
           "WHERE m.eventoTurno.eventoId = :eventoId " +
           "AND m.tipoMovimiento = :tipoMovimiento " +
           "AND m.rol.requiereAforo = true")
    long countMovimientosConAforo(@Param("eventoId") Long eventoId, @Param("tipoMovimiento") TipoMovimiento tipoMovimiento);

    @Query("SELECT COALESCE(SUM(CASE WHEN m.tipoMovimiento = com.sentinelmine.entity.enums.TipoMovimiento.ENTRADA THEN 1 ELSE -1 END), 0) " +
           "FROM MovimientoAforo m " +
           "WHERE m.eventoTurno.eventoId = :eventoId AND m.rol.requiereAforo = true")
    Integer calcularAforoNetoPorEvento(@Param("eventoId") Long eventoId);

    @Query("SELECT COALESCE(SUM(CASE WHEN m.tipoMovimiento = com.sentinelmine.entity.enums.TipoMovimiento.ENTRADA THEN 1 ELSE -1 END), 0) " +
           "FROM MovimientoAforo m " +
           "WHERE m.eventoTurno.estado = com.sentinelmine.entity.enums.EstadoTurno.ACTIVO AND m.rol.requiereAforo = true")
    Integer calcularAforoNetoGlobalActivo();

    @Query("SELECT m.rol.nombre, m.rol.colorCasco, " +
           "COALESCE(SUM(CASE WHEN m.tipoMovimiento = com.sentinelmine.entity.enums.TipoMovimiento.ENTRADA THEN 1 ELSE -1 END), 0) " +
           "FROM MovimientoAforo m " +
           "WHERE m.eventoTurno.estado = com.sentinelmine.entity.enums.EstadoTurno.ACTIVO " +
           "GROUP BY m.rol.nombre, m.rol.colorCasco")
    List<Object[]> findAforoPorRolActivo();

    @Query("SELECT m FROM MovimientoAforo m " +
           "JOIN FETCH m.rol " +
           "JOIN FETCH m.eventoTurno e " +
           "JOIN FETCH e.turno " +
           "ORDER BY m.fechaHora DESC")
    List<MovimientoAforo> findUltimosMovimientos(Pageable pageable);

    @Query("SELECT DISTINCT m.trackId FROM MovimientoAforo m " +
           "WHERE m.eventoTurno.estado = com.sentinelmine.entity.enums.EstadoTurno.ACTIVO " +
           "AND m.tipoMovimiento = com.sentinelmine.entity.enums.TipoMovimiento.ENTRADA " +
           "AND m.trackId IS NOT NULL")
    List<Integer> trackIdsConEntradaTurnoActivo();
}
