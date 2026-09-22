package com.sentinelmine.service;

import com.sentinelmine.dto.AforoDTO;
import com.sentinelmine.entity.EventoTurno;
import com.sentinelmine.entity.MovimientoAforo;
import com.sentinelmine.entity.RolPersonal;
import com.sentinelmine.entity.enums.EstadoTurno;
import com.sentinelmine.entity.enums.TipoMovimiento;
import com.sentinelmine.repository.EventoTurnoRepository;
import com.sentinelmine.repository.MovimientoAforoRepository;
import com.sentinelmine.repository.RolPersonalRepository;
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
 * Provee conteo de aforo dinámico y alimenta las vistas web de Thymeleaf.
 */
@Service
public class AforoService {

    private final MovimientoAforoRepository movimientoAforoRepository;
    private final EventoTurnoRepository eventoTurnoRepository;
    private final RolPersonalRepository rolPersonalRepository;

    @Value("${minesentinel.aforo.maximo:50}")
    private int aforoMaximo;

    @Value("${minesentinel.aforo.minimo:10}")
    private int aforoMinimo;

    private String ultimoEvento = "20:18:11 - Ingreso bloqueado — Trabajador #16, EPP incompleto";

    public AforoService(MovimientoAforoRepository movimientoAforoRepository,
                        EventoTurnoRepository eventoTurnoRepository,
                        RolPersonalRepository rolPersonalRepository) {
        this.movimientoAforoRepository = movimientoAforoRepository;
        this.eventoTurnoRepository = eventoTurnoRepository;
        this.rolPersonalRepository = rolPersonalRepository;
    }

    @Transactional
    public synchronized boolean registrarIngreso(String codigoTrabajador, boolean eppCompleto) {
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

        EventoTurno evento = obtenerOcrearEventoActivo();
        RolPersonal rol = obtenerRolDefault();

        MovimientoAforo movimiento = new MovimientoAforo(evento, rol, TipoMovimiento.ENTRADA, LocalDateTime.now());
        movimientoAforoRepository.save(movimiento);

        ultimoEvento = String.format("%s - Ingreso autorizado — Trabajador #%s (%s)",
                horaActual(), codigoTrabajador, rol.getNombre());
        return true;
    }

    @Transactional
    public synchronized void registrarSalida() {
        int aforoActual = getAforoActual();
        if (aforoActual > 0) {
            EventoTurno evento = obtenerOcrearEventoActivo();
            RolPersonal rol = obtenerRolDefault();

            MovimientoAforo movimiento = new MovimientoAforo(evento, rol, TipoMovimiento.SALIDA, LocalDateTime.now());
            movimientoAforoRepository.save(movimiento);

            ultimoEvento = String.format("%s - Salida registrada", horaActual());
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

    private EventoTurno obtenerOcrearEventoActivo() {
        List<EventoTurno> activos = eventoTurnoRepository.findByEstado(EstadoTurno.ACTIVO);
        if (!activos.isEmpty()) {
            return activos.get(0);
        }
        return eventoTurnoRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    EventoTurno nuevo = new EventoTurno();
                    nuevo.setEstado(EstadoTurno.ACTIVO);
                    nuevo.setFechaInicio(LocalDateTime.now());
                    return eventoTurnoRepository.save(nuevo);
                });
    }

    private RolPersonal obtenerRolDefault() {
        return rolPersonalRepository.findAll().stream().findFirst()
                .orElseGet(() -> rolPersonalRepository.save(new RolPersonal("Operador General", "Amarillo", true)));
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

        // Si hay movimientos recientes en base de datos, sincronizamos la descripción del último evento
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
