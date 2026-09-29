package com.sentinelmine.entity;

import jakarta.persistence.*;
import java.time.LocalTime;
import java.util.Objects;

/**
 * 2. Turnos: Definición horaria de los turnos base de operación minera (e.g. Turno Día 07:00-15:30, Turno Tarde 15:00-23:30).
 */
@Entity
@Table(name = "turnos")
public class Turno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "turno_id")
    private Long turnoId;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    public Turno() {
    }

    public Turno(String nombre, LocalTime horaInicio, LocalTime horaFin) {
        this.nombre = nombre;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
    }

    public Long getTurnoId() {
        return turnoId;
    }

    public void setTurnoId(Long turnoId) {
        this.turnoId = turnoId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public void setHoraInicio(LocalTime horaInicio) {
        this.horaInicio = horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }

    public void setHoraFin(LocalTime horaFin) {
        this.horaFin = horaFin;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Turno turno = (Turno) o;
        return Objects.equals(turnoId, turno.turnoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(turnoId);
    }

    @Override
    public String toString() {
        return "Turno{" +
                "turnoId=" + turnoId +
                ", nombre='" + nombre + '\'' +
                ", horaInicio=" + horaInicio +
                ", horaFin=" + horaFin +
                '}';
    }
}
