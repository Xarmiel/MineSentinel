package com.sentinelmine.service;

import com.sentinelmine.entity.enums.EstadoAlerta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EsquemaEstadoAlertaService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EsquemaEstadoAlertaService.class);
    private static final List<String> TABLAS = List.of("faltas_epp", "anomalias_movimiento");

    private final JdbcTemplate jdbcTemplate;

    public EsquemaEstadoAlertaService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        for (String tabla : TABLAS) {
            try {
                actualizar(tabla);
            } catch (Exception e) {
                log.warn("No se pudo actualizar el CHECK de estado_alerta en {}: {}", tabla, e.getMessage());
            }
        }
    }

    private void actualizar(String tabla) {
        List<String[]> checks = jdbcTemplate.query(
                "SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint "
                        + "WHERE conrelid = to_regclass(?) AND contype = 'c' "
                        + "AND pg_get_constraintdef(oid) LIKE '%estado_alerta%'",
                (rs, i) -> new String[]{rs.getString(1), rs.getString(2)}, tabla);

        boolean alDia = checks.size() == 1 && Arrays.stream(EstadoAlerta.values())
                .allMatch(e -> checks.get(0)[1].contains("'" + e.name() + "'"));
        if (alDia) {
            return;
        }

        String valores = Arrays.stream(EstadoAlerta.values())
                .map(e -> "'" + e.name() + "'")
                .collect(Collectors.joining(", "));
        for (String[] check : checks) {
            jdbcTemplate.execute("ALTER TABLE " + tabla + " DROP CONSTRAINT \"" + check[0] + "\"");
        }
        jdbcTemplate.execute("ALTER TABLE " + tabla + " ADD CONSTRAINT " + tabla
                + "_estado_alerta_check CHECK (estado_alerta IN (" + valores + "))");
        log.info("CHECK de estado_alerta en {} actualizado a: {}", tabla, valores);
    }
}
