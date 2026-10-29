package com.sentinelmine.repository;

import com.sentinelmine.entity.FaltaEPP;
import com.sentinelmine.entity.enums.EstadoAlerta;
import com.sentinelmine.entity.enums.TipoInfraccionEPP;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FaltaEPPRepository extends JpaRepository<FaltaEPP, Long> {

    List<FaltaEPP> findByEventoTurno_EventoId(Long eventoId);

    List<FaltaEPP> findByEstadoAlertaOrderByFechaHoraDesc(EstadoAlerta estadoAlerta);

    @Query("SELECT f FROM FaltaEPP f " +
           "JOIN FETCH f.epp " +
           "JOIN FETCH f.eventoTurno e " +
           "JOIN FETCH e.turno " +
           "LEFT JOIN FETCH f.rol " +
           "WHERE f.tipoInfraccion = :tipo " +
           "ORDER BY f.fechaHora DESC")
    List<FaltaEPP> findByTipoInfraccion(Pageable pageable, @Param("tipo") TipoInfraccionEPP tipo);

    @Query("SELECT f FROM FaltaEPP f " +
           "JOIN FETCH f.epp " +
           "JOIN FETCH f.eventoTurno e " +
           "JOIN FETCH e.turno " +
           "LEFT JOIN FETCH f.rol " +
           "ORDER BY f.fechaHora DESC")
    List<FaltaEPP> findUltimasFaltas(Pageable pageable);

    // Con open-in-view=false la entidad se serializa fuera de la sesión: sin
    // JOIN FETCH, Jackson recibe proxies LAZY y la respuesta falla con 500.
    @Query("SELECT f FROM FaltaEPP f " +
           "JOIN FETCH f.epp " +
           "JOIN FETCH f.eventoTurno e " +
           "JOIN FETCH e.turno " +
           "LEFT JOIN FETCH f.rol " +
           "WHERE f.faltaId = :id")
    Optional<FaltaEPP> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT f FROM FaltaEPP f " +
           "JOIN FETCH f.epp " +
           "JOIN FETCH f.eventoTurno e " +
           "WHERE f.estadoAlerta = :estado " +
           "ORDER BY f.fechaHora DESC")
    List<FaltaEPP> findByEstadoWithDetails(@Param("estado") EstadoAlerta estado);

    // KPIs del turno: se excluyen las DESCARTADA porque son falsos positivos del modelo.
    @Query("SELECT COUNT(f) FROM FaltaEPP f " +
           "WHERE f.eventoTurno.estado = com.sentinelmine.entity.enums.EstadoTurno.ACTIVO " +
           "AND f.estadoAlerta <> com.sentinelmine.entity.enums.EstadoAlerta.DESCARTADA")
    long contarInfraccionesTurnoActivo();

    @Query("SELECT f.epp.nombre, COUNT(f) FROM FaltaEPP f " +
           "WHERE f.eventoTurno.estado = com.sentinelmine.entity.enums.EstadoTurno.ACTIVO " +
           "AND f.estadoAlerta <> com.sentinelmine.entity.enums.EstadoAlerta.DESCARTADA " +
           "GROUP BY f.epp.nombre ORDER BY COUNT(f) DESC")
    List<Object[]> contarInfraccionesPorEppTurnoActivo();

    @Query("SELECT DISTINCT f.trackId FROM FaltaEPP f " +
           "WHERE f.eventoTurno.estado = com.sentinelmine.entity.enums.EstadoTurno.ACTIVO " +
           "AND f.estadoAlerta <> com.sentinelmine.entity.enums.EstadoAlerta.DESCARTADA " +
           "AND f.trackId IS NOT NULL")
    List<Integer> trackIdsConFaltaTurnoActivo();
}
