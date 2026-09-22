package com.sentinelmine.service;

import com.sentinelmine.dto.AforoDTO;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Lógica de negocio del control de aforo mediante línea virtual.
 * Simula el conteo de cruces (ingreso/salida) que en producción
 * llegaría desde el módulo de visión artificial (YOLOv8).
 */
@Service
public class AforoService {

    private int aforoActual = 42;
    private final int aforoMaximo = 50;
    private final int aforoMinimo = 10;
    private String ultimoEvento = "20:18:11 - Ingreso bloqueado — Trabajador #16, EPP incompleto";

    public synchronized boolean registrarIngreso(String codigoTrabajador, boolean eppCompleto) {
        if (!eppCompleto) {
            ultimoEvento = String.format("%s - Ingreso bloqueado — Trabajador #%s, EPP incompleto",
                    horaActual(), codigoTrabajador);
            return false;
        }
        if (aforoActual >= aforoMaximo) {
            ultimoEvento = String.format("%s - Ingreso bloqueado — Aforo máximo alcanzado", horaActual());
            return false;
        }
        aforoActual++;
        ultimoEvento = String.format("%s - Ingreso autorizado — Trabajador #%s", horaActual(), codigoTrabajador);
        return true;
    }

    public synchronized void registrarSalida() {
        if (aforoActual > 0) {
            aforoActual--;
            ultimoEvento = String.format("%s - Salida registrada", horaActual());
        }
    }

    private String horaActual() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    public int getPorcentajeCapacidad() {
        return (int) Math.round((aforoActual * 100.0) / aforoMaximo);
    }

    public String getEstadoAforo() {
        double pct = (aforoActual * 100.0) / aforoMaximo;
        if (pct >= 100) return "AFORO MÁXIMO";
        if (pct >= 90) return "AFORO CRÍTICO";
        return "AFORO NORMAL";
    }

    public AforoDTO toDTO() {
        return new AforoDTO(aforoActual, aforoMaximo, aforoMinimo, getPorcentajeCapacidad(), getEstadoAforo(), ultimoEvento);
    }
}
