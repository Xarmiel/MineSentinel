package com.sentinelmine.service;

import com.sentinelmine.dto.AforoDTO;
import com.sentinelmine.entity.ConfiguracionAforo;
import com.sentinelmine.entity.EventoTurno;
import com.sentinelmine.entity.MovimientoAforo;
import com.sentinelmine.entity.RolPersonal;
import com.sentinelmine.entity.enums.EstadoTurno;
import com.sentinelmine.entity.enums.TipoMovimiento;
import com.sentinelmine.repository.ConfiguracionAforoRepository;
import com.sentinelmine.repository.EventoTurnoRepository;
import com.sentinelmine.repository.MovimientoAforoRepository;
import com.sentinelmine.repository.RolPersonalRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Servicio de aforo conectado a base de datos relacional PostgreSQL / Supabase.
 * Implementa el patrón Append-Only Event Ledger libre de cuellos de botella de concurrencia.
 */
@Service
public class AforoService {

    private static final Logger log = LoggerFactory.getLogger(AforoService.class);

    private final MovimientoAforoRepository movimientoAforoRepository;
    private final EventoTurnoRepository eventoTurnoRepository;
    private final RolPersonalRepository rolPersonalRepository;
    private final EventoTurnoService eventoTurnoService;

    private final ConfiguracionAforoRepository configuracionAforoRepository;

    // Valores por defecto de application.properties; los reemplaza la fila de
    // configuracion_aforo si el administrador ya los cambió.
    @Value("${minesentinel.aforo.maximo:50}")
    private volatile int aforoMaximo;

    @Value("${minesentinel.aforo.minimo:10}")
    private volatile int aforoMinimo;

    private volatile String ultimoEvento = "20:18:11 - Ingreso bloqueado — Trabajador #16, EPP incompleto";

    public AforoService(MovimientoAforoRepository movimientoAforoRepository,
                        EventoTurnoRepository eventoTurnoRepository,
                        RolPersonalRepository rolPersonalRepository,
                        EventoTurnoService eventoTurnoService,
                        ConfiguracionAforoRepository configuracionAforoRepository) {
        this.movimientoAforoRepository = movimientoAforoRepository;
        this.eventoTurnoRepository = eventoTurnoRepository;
        this.rolPersonalRepository = rolPersonalRepository;
        this.eventoTurnoService = eventoTurnoService;
        this.configuracionAforoRepository = configuracionAforoRepository;
    }

    @PostConstruct
    void cargarLimites() {
        try {
            configuracionAforoRepository.findById(ConfiguracionAforo.ID_UNICO).ifPresent(c -> {
                aforoMaximo = c.getAforoMaximo();
                aforoMinimo = c.getAforoMinimo();
            });
        } catch (Exception e) {
            // Sin base de datos al arrancar se sigue con los valores por defecto.
            log.warn("No se pudieron cargar los límites de aforo guardados: {}", e.getMessage());
        }
    }

    public int getAforoMaximo() {
        return aforoMaximo;
    }

    public int getAforoMinimo() {
        return aforoMinimo;
    }

    /** Cambia y persiste los límites de aforo (sólo el administrador). */
    @Transactional
    public void actualizarLimites(int maximo, int minimo) {
        if (maximo < 1 || minimo < 0 || minimo > maximo) {
            throw new IllegalArgumentException("El máximo debe ser al menos 1 y el mínimo estar entre 0 y el máximo.");
        }
        configuracionAforoRepository.save(new ConfiguracionAforo(maximo, minimo));
        aforoMaximo = maximo;
        aforoMinimo = minimo;
    }

    /**
     * Registra el ingreso de un trabajador en tiempo real.
     * Operación transaccional libre de bloqueos de exclusión mutua global.
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public boolean registrarIngreso(String codigoTrabajador, boolean eppCompleto) {
        if (!eppCompleto) {
            ultimoEvento = String.format("%s - Ingreso bloqueado — Trabajador #%s, EPP incompleto",
                    horaActual(), codigoTrabajador);
            return false;
        }

        int aforoActual = getAforoActual();
        if (aforoActual >= aforoMaximo) {
            ultimoEvento = String.format("%s - Ingreso bloqueado — Aforo máximo alcanzado", horaActual());
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        EventoTurno evento = eventoTurnoService.resolverEventoActivoParaMovimiento(now, TipoMovimiento.ENTRADA);
        RolPersonal rol = obtenerRolDefault();

        MovimientoAforo movimiento = new MovimientoAforo(evento, rol, TipoMovimiento.ENTRADA, now);
        movimientoAforoRepository.save(movimiento);

        ultimoEvento = String.format("%s - Ingreso autorizado — Trabajador #%s (%s)",
                horaActual(), codigoTrabajador, rol.getNombre());
        return true;
    }

    /**
     * Registra la salida de un trabajador en tiempo real.
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void registrarSalida() {
        int aforoActual = getAforoActual();
        if (aforoActual > 0) {
            LocalDateTime now = LocalDateTime.now();
            EventoTurno evento = eventoTurnoService.resolverEventoActivoParaMovimiento(now, TipoMovimiento.SALIDA);
            RolPersonal rol = obtenerRolDefault();

            MovimientoAforo movimiento = new MovimientoAforo(evento, rol, TipoMovimiento.SALIDA, now);
            movimientoAforoRepository.save(movimiento);

            ultimoEvento = String.format("%s - Salida registrada", horaActual());
        }
    }

    @Transactional
    public void resetearAforo() {
        try {
            movimientoAforoRepository.deleteAll();
            ultimoEvento = "Aforo reiniciado a 0 - Sin movimientos";
        } catch (Exception e) {
            // Manejo defensivo
        }
    }

    @Transactional(readOnly = true)
    public int getAforoActual() {
        try {
            Integer total = movimientoAforoRepository.calcularAforoNetoGlobalActivo();
            return Math.max(0, total != null ? total : 0);
        } catch (Exception e) {
            return 0;
        }
    }

    private RolPersonal obtenerRolDefault() {
        return rolPersonalRepository.findAll().stream().findFirst()
                .orElseGet(() -> rolPersonalRepository.save(new RolPersonal("Operador de Maquinaria / Perforista", "Amarillo", true)));
    }

    private String horaActual() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    public int getPorcentajeCapacidad() {
        int actual = getAforoActual();
        return (int) Math.round((actual * 100.0) / aforoMaximo);
    }

    public String getEstadoAforo() {
        double pct = (getAforoActual() * 100.0) / aforoMaximo;
        if (pct >= 100) return "AFORO MÁXIMO";
        if (pct >= 90) return "AFORO CRÍTICO";
        return "AFORO NORMAL";
    }

    @Transactional(readOnly = true)
    public AforoDTO toDTO() {
        int actual = getAforoActual();
        int porcentaje = getPorcentajeCapacidad();
        String estado = getEstadoAforo();

        try {
            List<MovimientoAforo> ultimos = movimientoAforoRepository.findUltimosMovimientos(PageRequest.of(0, 1));
            if (!ultimos.isEmpty()) {
                MovimientoAforo m = ultimos.get(0);
                String hora = m.getFechaHora().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                ultimoEvento = String.format("%s - %s registrado — %s (Casco %s)",
                        hora,
                        m.getTipoMovimiento() == TipoMovimiento.ENTRADA ? "Ingreso autorizado" : "Salida",
                        m.getRol().getNombre(),
                        m.getRol().getColorCasco() != null ? m.getRol().getColorCasco() : "Estándar");
            }
        } catch (Exception ignored) {
        }

        return new AforoDTO(actual, aforoMaximo, aforoMinimo, porcentaje, estado, ultimoEvento);
    }
}
