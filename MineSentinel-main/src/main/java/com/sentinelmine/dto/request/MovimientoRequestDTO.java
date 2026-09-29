package com.sentinelmine.dto.request;

import com.sentinelmine.entity.enums.TipoMovimiento;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Payload emitido por el servicio de visión artificial (ByteTrack) al registrar un cruce de línea.
 */
public class MovimientoRequestDTO {

    // Opcional: Si se omite, el sistema resuelve automáticamente el evento_id activo
    // según el flujo operativo y el solapamiento de turnos (ENTRADA -> nuevo turno / SALIDA -> turno saliente)
    private Long eventoId;

    @NotNull(message = "El rolId es obligatorio")
    private Long rolId;

    @NotNull(message = "El tipo de movimiento (ENTRADA/SALIDA) es obligatorio")
    private TipoMovimiento tipoMovimiento;

    // Opcional: identificador del track de ByteTrack que cruzó la línea virtual.
    private Integer trackId;

    private LocalDateTime fechaHora;

    public MovimientoRequestDTO() {
    }

    public MovimientoRequestDTO(Long eventoId, Long rolId, TipoMovimiento tipoMovimiento, LocalDateTime fechaHora) {
        this(eventoId, rolId, tipoMovimiento, fechaHora, null);
    }

    public MovimientoRequestDTO(Long eventoId, Long rolId, TipoMovimiento tipoMovimiento,
                                LocalDateTime fechaHora, Integer trackId) {
        this.eventoId = eventoId;
        this.rolId = rolId;
        this.tipoMovimiento = tipoMovimiento;
        this.fechaHora = fechaHora;
        this.trackId = trackId;
    }

    public Long getEventoId() {
        return eventoId;
    }

    public void setEventoId(Long eventoId) {
        this.eventoId = eventoId;
    }

    public Long getRolId() {
        return rolId;
    }

    public void setRolId(Long rolId) {
        this.rolId = rolId;
    }

    public TipoMovimiento getTipoMovimiento() {
        return tipoMovimiento;
    }

    public void setTipoMovimiento(TipoMovimiento tipoMovimiento) {
        this.tipoMovimiento = tipoMovimiento;
    }

    public Integer getTrackId() {
        return trackId;
    }

    public void setTrackId(Integer trackId) {
        this.trackId = trackId;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora != null ? fechaHora : LocalDateTime.now();
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }
}
