package com.sentinelmine.service;

import com.sentinelmine.config.AuthInterceptor;
import com.sentinelmine.entity.*;
import com.sentinelmine.entity.enums.EstadoAlerta;
import com.sentinelmine.entity.enums.EstadoTurno;
import com.sentinelmine.entity.enums.TipoCatalogoEPP;
import com.sentinelmine.entity.enums.TipoMovimiento;
import com.sentinelmine.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    // Vacías = no se fuerzan; así un arranque local no pisa la contraseña de producción (misma BD).
    @Value("${minesentinel.admin.password:}")
    private String adminPassword = "";
    @Value("${minesentinel.supervisor.password:}")
    private String supervisorPassword = "";

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
            inicializarSupervisor();
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
                    passwordEncoder.encode(adminPassword.isEmpty() ? "sentinel123" : adminPassword),
                    "Administrador del Sistema",
                    "ADMIN",
                    true
            );
            usuarioRepository.save(admin);
            log.info("Usuario administrador por defecto 'admin' creado exitosamente.");
        } else {
            Usuario admin = adminOpt.get();
            if (admin.getEstadoActivo() == null || !admin.getEstadoActivo()) {
                admin.setEstadoActivo(true);
                admin.setNombreCompleto("Administrador del Sistema");
                usuarioRepository.save(admin);
                log.info("Usuario 'admin' reactivado.");
            }
            if (!adminPassword.isEmpty() && !passwordEncoder.matches(adminPassword, admin.getPassword())) {
                admin.setPassword(passwordEncoder.encode(adminPassword));
                usuarioRepository.save(admin);
                log.info("Contraseña del usuario 'admin' sincronizada con ADMIN_PASSWORD.");
            }
            if (!AuthInterceptor.ROL_ADMIN.equals(admin.getRol())) {
                admin.setRol(AuthInterceptor.ROL_ADMIN);
                usuarioRepository.save(admin);
                log.info("Rol del usuario 'admin' normalizado a ADMIN.");
            }
        }
    }

    private void inicializarSupervisor() {
        Optional<Usuario> supOpt = usuarioRepository.findByUsername("supervisor");
        if (supOpt.isEmpty()) {
            usuarioRepository.save(new Usuario(
                    "supervisor",
                    passwordEncoder.encode(supervisorPassword.isEmpty() ? "supervisor123" : supervisorPassword),
                    "Supervisor de Turno",
                    AuthInterceptor.ROL_SUPERVISOR,
                    true
            ));
            log.info("Usuario supervisor por defecto 'supervisor' creado exitosamente.");
        } else if (!supervisorPassword.isEmpty() && !passwordEncoder.matches(supervisorPassword, supOpt.get().getPassword())) {
            supOpt.get().setPassword(passwordEncoder.encode(supervisorPassword));
            usuarioRepository.save(supOpt.get());
            log.info("Contraseña del usuario 'supervisor' sincronizada con SUPERVISOR_PASSWORD.");
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
        List<String> existentes = catalogoEPPRepository.findAll().stream()
                .map(c -> c.getNombre().toLowerCase().trim())
                .toList();

        List<CatalogoEPP> nuevos = new ArrayList<>();
        for (CatalogoEPP semilla : catalogoEPPSemilla()) {
            if (!existentes.contains(semilla.getNombre().toLowerCase().trim())) {
                nuevos.add(semilla);
            }
        }

        if (!nuevos.isEmpty()) {
            catalogoEPPRepository.saveAll(nuevos);
            log.info("Catálogo de EPP: {} elemento(s) agregados ({}).", nuevos.size(),
                    nuevos.stream().map(c -> c.getNombre() + " [" + c.getTipo() + "]").toList());
        }

        List<CatalogoEPP> porCorregir = catalogoEPPRepository.findAll().stream()
                .filter(c -> c.getTipo() == null)
                .toList();
        if (!porCorregir.isEmpty()) {
            porCorregir.forEach(c -> c.setTipo(TipoCatalogoEPP.CONFORME));
            catalogoEPPRepository.saveAll(porCorregir);
            log.info("Catálogo de EPP: {} fila(s) anteriores marcadas como CONFORME.", porCorregir.size());
        }
    }

    private List<CatalogoEPP> catalogoEPPSemilla() {
        return List.of(
                new CatalogoEPP("Casco de Seguridad con Barbiquejo", TipoCatalogoEPP.CONFORME),
                new CatalogoEPP("Chaleco Reflectivo de Alta Visibilidad", TipoCatalogoEPP.CONFORME),
                new CatalogoEPP("Lámpara Minera Frontal", TipoCatalogoEPP.CONFORME),
                new CatalogoEPP("Respirador contra Polvo / Vapores", TipoCatalogoEPP.CONFORME),
                new CatalogoEPP("Botas de Seguridad con Puntera de Acero", TipoCatalogoEPP.CONFORME),
                new CatalogoEPP("Gafas de Seguridad / Protectoras", TipoCatalogoEPP.CONFORME),
                new CatalogoEPP("Guantes de Seguridad", TipoCatalogoEPP.CONFORME),
                new CatalogoEPP("Mascarilla / Respirador contra Polvo", TipoCatalogoEPP.CONFORME),
                new CatalogoEPP("Gorra / Sombrero No Apto como Casco", TipoCatalogoEPP.IMPOSTOR),
                new CatalogoEPP("Gafas de Sol No Aptas como Gafas de Seguridad", TipoCatalogoEPP.IMPOSTOR),
                new CatalogoEPP("Calzado Normal / Zapatillas Deportivas", TipoCatalogoEPP.IMPOSTOR)
        );
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
