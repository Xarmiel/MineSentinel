package com.sentinelmine.entity;

import jakarta.persistence.*;
import java.util.Objects;

/**
 * 1. RolesPersonal: Catálogo de roles operativos en mina (e.g. Perforista, Supervisor, Geólogo, Visitante).
 * Relaciona el color de casco reglamentario y si su presencia suma a la capacidad máxima de socavón.
 */
@Entity
@Table(name = "roles_personal")
public class RolPersonal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rol_id")
    private Long rolId;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "color_casco", length = 50)
    private String colorCasco;

    @Column(name = "requiere_aforo", nullable = false)
    private Boolean requiereAforo = true;

    public RolPersonal() {
    }

    public RolPersonal(String nombre, String colorCasco, Boolean requiereAforo) {
        this.nombre = nombre;
        this.colorCasco = colorCasco;
        this.requiereAforo = requiereAforo;
    }

    public Long getRolId() {
        return rolId;
    }

    public void setRolId(Long rolId) {
        this.rolId = rolId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getColorCasco() {
        return colorCasco;
    }

    public void setColorCasco(String colorCasco) {
        this.colorCasco = colorCasco;
    }

    public Boolean getRequiereAforo() {
        return requiereAforo;
    }

    public void setRequiereAforo(Boolean requiereAforo) {
        this.requiereAforo = requiereAforo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RolPersonal that = (RolPersonal) o;
        return Objects.equals(rolId, that.rolId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rolId);
    }

    @Override
    public String toString() {
        return "RolPersonal{" +
                "rolId=" + rolId +
                ", nombre='" + nombre + '\'' +
                ", colorCasco='" + colorCasco + '\'' +
                ", requiereAforo=" + requiereAforo +
                '}';
    }
}
