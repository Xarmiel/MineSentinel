package com.sentinelmine.service;

import com.sentinelmine.entity.*;
import com.sentinelmine.entity.enums.EstadoAlerta;
import com.sentinelmine.entity.enums.EstadoTurno;
import com.sentinelmine.entity.enums.TipoMovimiento;
import com.sentinelmine.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Inicializador de datos de catálogo y estado inicial en Supabase/PostgreSQL.
 * Garantiza que la aplicación arranque con datos válidos de turnos, EPP, anomalías, roles y usuarios.
 */
@Service
public class DatabaseInitializerService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializerService.class);

    private final RolPersonalRepository rolPersonalRepository;
    private final TurnoRepository turnoRepository;
    private final CatalogoEPPRepository catalogoEPPRepository;
    private final CatalogoAnomaliasRepository catalogoAnomaliasRepository;
    private final EventoTurnoRepository eventoTurnoRepository;
    private final MovimientoAforoRepository movimientoAforoRepository;
    private final FaltaEPPRepository faltaEPPRepository;
    private final AnomaliaMovimientoRepository anomaliaMovimientoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public DatabaseInitializerService(RolPersonalRepository rolPersonalRepository,
                                      TurnoRepository turnoRepository,
                                      CatalogoEPPRepository catalogoEPPRepository,
                                      CatalogoAnomaliasRepository catalogoAnomaliasRepository,
                                      EventoTurnoRepository eventoTurnoRepository,
                                      MovimientoAforoRepository movimientoAforoRepository,
                                      FaltaEPPRepository faltaEPPRepository,
                                      AnomaliaMovimientoRepository anomaliaMovimientoRepository,
                                      UsuarioRepository usuarioRepository,
                                      PasswordEncoder passwordEncoder) {
        this.rolPersonalRepository = rolPersonalRepository;
        this.turnoRepository = turnoRepository;
        this.catalogoEPPRepository = catalogoEPPRepository;
        this.catalogoAnomaliasRepository = catalogoAnomaliasRepository;
        this.eventoTurnoRepository = eventoTurnoRepository;
        this.movimientoAforoRepository = movimientoAforoRepository;
        this.faltaEPPRepository = faltaEPPRepository;
        this.anomaliaMovimientoRepository = anomaliaMovimientoRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        try {
            log.info("Verificando catálogos y estado inicial en base de datos...");
            inicializarUsuarios();
            inicializarRoles();
            inicializarTurnos();
            inicializarCatalogoEPP();
            inicializarCatalogoAnomalias();
            inicializarTurnosActivosYMovimientos();
            log.info("Inicialización de datos completada.");
        } catch (Exception e) {
            log.warn("Aviso en inicialización de base de datos (las tablas pueden ya existir o requerir conexión activa): {}", e.getMessage());
        }
    }

    private void inicializarUsuarios() {
        Optional<Usuario> adminOpt = usuarioRepository.findByUsername("admin");
        if (adminOpt.isEmpty()) {
            Usuario admin = new Usuario(
                    "admin",
                    passwordEncoder.encode("sentinel123"),
                    "Administrador del Sistema",
                    "ADMIN",
                    true
            );
            usuarioRepository.save(admin);
            log.info("Usuario administrador por defecto 'admin' creado exitosamente.");
        } else {
            Usuario admin = adminOpt.get();
            if (admin.getEstadoActivo() == null || !admin.getEstadoActivo() || !passwordEncoder.matches("sentinel123", admin.getPassword())) {
                admin.setPassword(passwordEncoder.encode("sentinel123"));
                admin.setEstadoActivo(true);
                admin.setNombreCompleto("Administrador del Sistema");
                usuarioRepository.save(admin);
                log.info("Credenciales del usuario 'admin' actualizadas a 'sentinel123'.");
            }
        }
    }

    private void inicializarRoles() {
        if (rolPersonalRepository.count() == 0) {
            rolPersonalRepository.saveAll(List.of(
                    new RolPersonal("Operador de Maquinaria / Perforista", "Amarillo", true),
                    new RolPersonal("Supervisor de Seguridad / Ingeniero", "Blanco", true),
                    new RolPersonal("Técnico Electricista / Mantenimiento", "Azul", true),
                    new RolPersonal("Geólogo / Topógrafo", "Verde", true),
                    new RolPersonal("Visitante Técnico / Auditor", "Rojo", true)
            ));
            log.info("Roles de personal inicializados.");
        }
    }

    private void inicializarTurnos() {
        if (turnoRepository.count() == 0) {
            turnoRepository.saveAll(List.of(
                    new Turno("Turno A · Guardia Día (Mañana)", LocalTime.of(7, 0), LocalTime.of(15, 30)),
                    new Turno("Turno B · Guardia Tarde", LocalTime.of(15, 0), LocalTime.of(23, 30)),
                    new Turno("Turno C · Guardia Noche", LocalTime.of(23, 0), LocalTime.of(7, 30))
            ));
            log.info("Turnos base inicializados.");
        }
    }

    private void inicializarCatalogoEPP() {
        if (catalogoEPPRepository.count() == 0) {
            catalogoEPPRepository.saveAll(List.of(
                    new CatalogoEPP("Casco de Seguridad con Barbiquejo"),
                    new CatalogoEPP("Chaleco Reflectivo de Alta Visibilidad"),
                    new CatalogoEPP("Lámpara Minera Frontal"),
                    new CatalogoEPP("Respirador contra Polvo / Vapores"),
                    new CatalogoEPP("Botas de Seguridad con Puntera de Acero")
            ));
            log.info("Catálogo de EPP inicializado.");
        }
    }

    private void inicializarCatalogoAnomalias() {
        if (catalogoAnomaliasRepository.count() == 0) {
            catalogoAnomaliasRepository.saveAll(List.of(
                    new CatalogoAnomalias("Cruce en Sentido Contrario al Flujo"),
                    new CatalogoAnomalias("Aglomeración en Boca-Mina (> 5 personas)"),
                    new CatalogoAnomalias("Ingreso sin Autorización de Guardia"),
                    new CatalogoAnomalias("Tiempo Excesivo en Zona Crítica de Tránsito"),
                    new CatalogoAnomalias("Detección de Caída o Postura Anómala")
            ));
            log.info("Catálogo de Anomalías inicializado.");
        }
    }

    private void inicializarTurnosActivosYMovimientos() {
        if (eventoTurnoRepository.findByEstado(EstadoTurno.ACTIVO).isEmpty()) {
            Turno turnoA = turnoRepository.findAll().stream().findFirst().orElse(null);
            if (turnoA != null) {
                EventoTurno evento = new EventoTurno(turnoA, LocalDateTime.now().minusHours(2));
                evento.setEstado(EstadoTurno.ACTIVO);
                EventoTurno eventoGuardado = eventoTurnoRepository.save(evento);

                RolPersonal rolPerforista = rolPersonalRepository.findAll().stream().findFirst().orElse(null);
                CatalogoEPP eppCasco = catalogoEPPRepository.findAll().stream().findFirst().orElse(null);

                // Sembrar movimientos iniciales de demostración para visualización en dashboard
                if (rolPerforista != null) {
                    for (int i = 0; i < 42; i++) {
                        movimientoAforoRepository.save(new MovimientoAforo(
                                eventoGuardado,
                                rolPerforista,
                                TipoMovimiento.ENTRADA,
                                LocalDateTime.now().minusMinutes(120 - (i * 2))
                        ));
                    }
                }

                // Sembrar alerta inicial de demostración
                if (eppCasco != null) {
                    faltaEPPRepository.save(new FaltaEPP(
                            eventoGuardado,
                            rolPerforista,
                            eppCasco,
                            0.94f,
                            null,
                            EstadoAlerta.PENDIENTE,
                            LocalDateTime.now().minusMinutes(15)
                    ));
                }

                log.info("Evento de turno activo inicial y movimientos sembrados.");
            }
        }
    }
}
