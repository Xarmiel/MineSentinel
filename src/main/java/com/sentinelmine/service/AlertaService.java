package com.sentinelmine.service;

import com.sentinelmine.model.Alerta;
import com.sentinelmine.model.Prioridad;
import com.sentinelmine.model.TipoAlerta;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Gestión del panel de alertas: registro, consulta y escalamiento
 * ("Notificar al Jefe de Turno") de infracciones detectadas.
 */
@Service
public class AlertaService {

    private final List<Alerta> alertas = new LinkedList<>();

    public AlertaService() {
        // Datos semilla que replican el prototipo original (Figma / Canva)
        alertas.add(new Alerta(TipoAlerta.EPP_INCOMPLETO, "16",
                "Sin Chaleco Reflectante en el punto de control de ingreso.", Prioridad.MEDIA));
        alertas.add(new Alerta(TipoAlerta.SALUD_POSTURA, "01",
                "Posible estado no óptimo: marcha inestable / tambaleo detectado.", Prioridad.ALTA));
    }

    public synchronized Alerta registrarAlerta(TipoAlerta tipo, String trabajadorCodigo, String descripcion, Prioridad prioridad) {
        Alerta alerta = new Alerta(tipo, trabajadorCodigo, descripcion, prioridad);
        alertas.add(0, alerta);
        return alerta;
    }

    public List<Alerta> getAlertasRecientes(int cantidad) {
        return alertas.stream().limit(cantidad).toList();
    }

    public List<Alerta> getHistorialCompleto() {
        return Collections.unmodifiableList(new LinkedList<>(alertas));
    }

    public synchronized boolean notificarJefeTurno(int idAlerta) {
        for (Alerta a : alertas) {
            if (a.getId() == idAlerta) {
                a.marcarNotificado();
                return true;
            }
        }
        return false;
    }
}
