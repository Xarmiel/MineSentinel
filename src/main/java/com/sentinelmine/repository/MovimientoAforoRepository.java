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

    /**
     * Cuenta el total de movimientos de un tipo específico (ENTRADA o SALIDA) para un evento de turno.
     */
    long countByEventoTurno_EventoIdAndTipoMovimiento(Long eventoId, TipoMovimiento tipoMovimiento);

    /**
     * Cuenta movimientos de un tipo específico para un evento de turno,
     * filtrando exclusivamente los roles que tienen requiereAforo = true.
     */
    long countByEventoTurno_EventoIdAndTipoMovimientoAndRol_RequiereAforoTrue(Long eventoId, TipoMovimiento tipoMovimiento);

    /**
     * Cuenta los movimientos con requiereAforo = true usando consulta explícita JPQL.
     */
    @Query("SELECT COUNT(m) FROM MovimientoAforo m " +
           "WHERE m.eventoTurno.eventoId = :eventoId " +
           "AND m.tipoMovimiento = :tipoMovimiento " +
           "AND m.rol.requiereAforo = true")
    long countMovimientosConAforo(@Param("eventoId") Long eventoId, @Param("tipoMovimiento") TipoMovimiento tipoMovimiento);

    /**
     * Calcula el aforo neto de personal en socavón para un evento de turno específico.
     * Solo considera roles donde requiereAforo = true.
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN m.tipoMovimiento = com.sentinelmine.entity.enums.TipoMovimiento.ENTRADA THEN 1 ELSE -1 END), 0) " +
           "FROM MovimientoAforo m " +
           "WHERE m.eventoTurno.eventoId = :eventoId AND m.rol.requiereAforo = true")
    Integer calcularAforoNetoPorEvento(@Param("eventoId") Long eventoId);

    /**
     * Calcula el aforo total global acumulado en tiempo real en la mina
     * considerando todos los turnos activos en paralelo (solapamiento de turnos).
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN m.tipoMovimiento = com.sentinelmine.entity.enums.TipoMovimiento.ENTRADA THEN 1 ELSE -1 END), 0) " +
           "FROM MovimientoAforo m " +
           "WHERE m.eventoTurno.estado = com.sentinelmine.entity.enums.EstadoTurno.ACTIVO AND m.rol.requiereAforo = true")
    Integer calcularAforoNetoGlobalActivo();

    /**
     * Agrupa y calcula el conteo actual de personal activo desglosado por rol y color de casco.
     */
    @Query("SELECT m.rol.nombre, m.rol.colorCasco, " +
           "COALESCE(SUM(CASE WHEN m.tipoMovimiento = com.sentinelmine.entity.enums.TipoMovimiento.ENTRADA THEN 1 ELSE -1 END), 0) " +
           "FROM MovimientoAforo m " +
           "WHERE m.eventoTurno.estado = com.sentinelmine.entity.enums.EstadoTurno.ACTIVO " +
           "GROUP BY m.rol.nombre, m.rol.colorCasco")
    List<Object[]> findAforoPorRolActivo();

    /**
     * Lista los últimos cruces de línea virtual registrados por ByteTrack para visualización en tiempo real.
     */
    @Query("SELECT m FROM MovimientoAforo m " +
           "JOIN FETCH m.rol " +
           "JOIN FETCH m.eventoTurno e " +
           "JOIN FETCH e.turno " +
           "ORDER BY m.fechaHora DESC")
    List<MovimientoAforo> findUltimosMovimientos(Pageable pageable);
}
