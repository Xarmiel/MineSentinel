package com.sentinelmine.repository;

import com.sentinelmine.dto.AlertaViewDTO;
import com.sentinelmine.dto.HistorialFiltro;
import com.sentinelmine.dto.HistorialPagina;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Consulta unificada del historial de alertas.
 *
 * <p>Las faltas de EPP y las anomalías viven en tablas distintas con claves
 * primarias independientes. Unirlas en memoria y ordenar por {@code id} producía
 * un orden incorrecto y limitaba el histórico a las últimas 50 filas de cada
 * tabla. Esta implementación hace el UNION ALL en PostgreSQL, filtra y pagina
 * antes de traer los datos a la aplicación.</p>
 */
@Repository
public class HistorialRepository {

    private static final DateTimeFormatter FORMATO_FECHA_HORA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FORMATO_HORA =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final String UNION_ALERTAS = """
            SELECT 'EPP' AS tipo,
                   f.falta_id AS id,
                   f.fecha_hora AS fecha_hora,
                   f.estado_alerta AS estado,
                   f.track_id AS track_id,
                   f.nivel_confianza AS nivel_confianza,
                   f.snapshot_url AS snapshot_url,
                   f.descripcion AS descripcion,
                   ce.nombre AS epp_nombre,
                   rp.nombre AS rol_nombre,
                   t.nombre AS turno_nombre,
                   f.tipo_infraccion AS tipo_infraccion,
                   NULL::varchar AS anomalia_nombre
            FROM faltas_epp f
            JOIN catalogo_epp ce ON ce.epp_id = f.epp_id
            JOIN eventos_turno e ON e.evento_id = f.evento_id
            JOIN turnos t ON t.turno_id = e.turno_id
            LEFT JOIN roles_personal rp ON rp.rol_id = f.rol_id
            UNION ALL
            SELECT 'ANOMALIA' AS tipo,
                   a.anomalia_id AS id,
                   a.fecha_hora AS fecha_hora,
                   a.estado_alerta AS estado,
                   NULL::int AS track_id,
                   a.nivel_confianza AS nivel_confianza,
                   a.snapshot_url AS snapshot_url,
                   NULL::varchar AS descripcion,
                   NULL::varchar AS epp_nombre,
                   rp.nombre AS rol_nombre,
                   t.nombre AS turno_nombre,
                   NULL::varchar AS tipo_infraccion,
                   ca.nombre AS anomalia_nombre
            FROM anomalias_movimiento a
            JOIN catalogo_anomalias ca ON ca.catalogo_anomalia_id = a.catalogo_anomalia_id
            JOIN eventos_turno e ON e.evento_id = a.evento_id
            JOIN turnos t ON t.turno_id = e.turno_id
            LEFT JOIN roles_personal rp ON rp.rol_id = a.rol_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public HistorialRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public HistorialPagina buscar(HistorialFiltro filtro) {
        MapSqlParameterSource parametros = new MapSqlParameterSource();
        String where = construirWhere(filtro, parametros);

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM (" + UNION_ALERTAS + ") h " + where,
                parametros,
                Long.class);

        String sql = "SELECT h.* FROM (" + UNION_ALERTAS + ") h " + where +
                " ORDER BY h.fecha_hora DESC, h.id DESC, h.tipo ASC" +
                " LIMIT :limite OFFSET :desplazamiento";
        parametros.addValue("limite", filtro.getSize());
        parametros.addValue("desplazamiento", filtro.getOffset());

        List<AlertaViewDTO> contenido = jdbc.query(sql, parametros, this::mapear);

        return new HistorialPagina(contenido, total != null ? total : 0L,
                filtro.getPage(), filtro.getSize());
    }

    public List<AlertaViewDTO> buscarLimitado(HistorialFiltro filtro, int limite) {
        MapSqlParameterSource parametros = new MapSqlParameterSource();
        String where = construirWhere(filtro, parametros);
        String sql = "SELECT h.* FROM (" + UNION_ALERTAS + ") h " + where +
                " ORDER BY h.fecha_hora DESC, h.id DESC, h.tipo ASC" +
                " LIMIT :limite";
        parametros.addValue("limite", limite);
        return jdbc.query(sql, parametros, this::mapear);
    }

    private String construirWhere(HistorialFiltro filtro, MapSqlParameterSource parametros) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");

        if (filtro.getDesde() != null) {
            where.append(" AND h.fecha_hora >= :desde");
            parametros.addValue("desde", filtro.getDesde().atStartOfDay());
        }
        if (filtro.getHasta() != null) {
            where.append(" AND h.fecha_hora < :hasta");
            parametros.addValue("hasta", filtro.getHasta().plusDays(1).atStartOfDay());
        }
        if (filtro.getTipo() != null) {
            where.append(" AND h.tipo = :tipo");
            parametros.addValue("tipo", filtro.getTipo());
        }
        if (filtro.getEstado() != null) {
            where.append(" AND h.estado = :estado");
            parametros.addValue("estado", filtro.getEstado());
        }
        if (filtro.getTrackId() != null) {
            where.append(" AND h.track_id = :trackId");
            parametros.addValue("trackId", filtro.getTrackId());
        }
        if (filtro.getQ() != null) {
            where.append("""
                     AND (
                        COALESCE(h.descripcion, '') ILIKE :q
                        OR COALESCE(h.epp_nombre, '') ILIKE :q
                        OR COALESCE(h.anomalia_nombre, '') ILIKE :q
                        OR COALESCE(h.rol_nombre, '') ILIKE :q
                        OR COALESCE(h.turno_nombre, '') ILIKE :q
                        OR COALESCE(CAST(h.track_id AS TEXT), '') LIKE :q
                     )
                    """);
            parametros.addValue("q", "%" + filtro.getQ() + "%");
        }

        return where.toString();
    }

    private AlertaViewDTO mapear(ResultSet rs, int rowNum) throws SQLException {
        String tipo = rs.getString("tipo");
        Long id = rs.getLong("id");
        LocalDateTime fechaHora = rs.getObject("fecha_hora", LocalDateTime.class);
        String estado = rs.getString("estado");
        Integer trackId = numeroEntero(rs.getObject("track_id"));
        Float confianza = numeroFloat(rs.getObject("nivel_confianza"));
        String snapshot = rs.getString("snapshot_url");
        String descripcionRegistrada = rs.getString("descripcion");
        String eppNombre = rs.getString("epp_nombre");
        String anomaliaNombre = rs.getString("anomalia_nombre");
        String rolNombre = rs.getString("rol_nombre");
        String turnoNombre = rs.getString("turno_nombre");
        String tipoInfraccion = rs.getString("tipo_infraccion");

        boolean esAnomalia = "ANOMALIA".equalsIgnoreCase(tipo);
        boolean esImpostor = "IMPOSTOR".equalsIgnoreCase(tipoInfraccion);
        boolean resuelta = "RESUELTA".equalsIgnoreCase(estado);

        String titulo;
        String descripcion;
        String prioridad;
        boolean prioridadAlta;

        if (esAnomalia) {
            titulo = "Alerta de seguridad / postura";
            descripcion = (anomaliaNombre != null ? anomaliaNombre : "Anomalía no clasificada")
                    + " detectada en cámara.";
            prioridad = "alta";
            prioridadAlta = true;
        } else {
            titulo = esImpostor
                    ? "EPP no homologado detectado"
                    : "Falta de EPP detectada";
            if (rolNombre != null && !rolNombre.isBlank()) {
                titulo += " (" + rolNombre + ")";
            }

            if (resuelta) {
                descripcion = (eppNombre != null ? eppNombre : "EPP")
                        + " repuesto. Infracción cerrada automáticamente por visión.";
            } else if (descripcionRegistrada != null && !descripcionRegistrada.isBlank()) {
                descripcion = descripcionRegistrada;
            } else if (esImpostor) {
                descripcion = "Elemento no homologado en uso: "
                        + (eppNombre != null ? eppNombre : "EPP no apto") + ".";
            } else {
                descripcion = "Sin " + (eppNombre != null ? eppNombre : "EPP")
                        + " en el punto de control de ingreso.";
            }
            prioridad = esImpostor ? "alta" : "media";
            prioridadAlta = esImpostor;
        }

        boolean notificado = "NOTIFICADA".equalsIgnoreCase(estado)
                || "REVISADA".equalsIgnoreCase(estado)
                || "RESUELTA".equalsIgnoreCase(estado);

        String trabajadorCodigo = trackId != null
                ? String.valueOf(trackId)
                : (rolNombre != null && !rolNombre.isBlank()
                        ? rolNombre
                        : (esAnomalia ? "Punto de control" : "N/I"));

        AlertaViewDTO dto = new AlertaViewDTO(
                id,
                tipo,
                titulo,
                fechaHora != null ? fechaHora.format(FORMATO_HORA) : "",
                descripcion,
                trabajadorCodigo,
                prioridad,
                prioridadAlta,
                notificado,
                snapshot,
                estado);

        dto.setFechaHora(fechaHora);
        if (fechaHora != null) {
            dto.setFechaFormateada(fechaHora.format(FORMATO_FECHA_HORA));
        }
        dto.setTrackId(trackId);
        dto.setRolNombre(rolNombre);
        dto.setNivelConfianza(confianza);
        dto.setTurnoNombre(turnoNombre);
        dto.setTipoInfraccion(tipoInfraccion);
        return dto;
    }

    private static Integer numeroEntero(Object valor) {
        return valor instanceof Number numero ? numero.intValue() : null;
    }

    private static Float numeroFloat(Object valor) {
        return valor instanceof Number numero ? numero.floatValue() : null;
    }
}
