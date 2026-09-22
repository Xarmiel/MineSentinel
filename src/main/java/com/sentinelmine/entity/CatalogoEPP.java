package com.sentinelmine.entity;

import jakarta.persistence.*;
import java.util.Objects;

/**
 * 3. CatalogoEPP: Elementos de Protección Personal monitoreados por YOLOv8 (e.g. Casco, Chaleco Reflectivo, Lámpara Minera, Barbiquejo, Botas).
 */
@Entity
@Table(name = "catalogo_epp")
public class CatalogoEPP {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "epp_id")
    private Long eppId;

    @Column(name = "nombre", nullable = false, length = 100, unique = true)
    private String nombre;

    public CatalogoEPP() {
    }

    public CatalogoEPP(String nombre) {
        this.nombre = nombre;
    }

    public Long getEppId() {
        return eppId;
    }

    public void setEppId(Long eppId) {
        this.eppId = eppId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CatalogoEPP that = (CatalogoEPP) o;
        return Objects.equals(eppId, that.eppId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eppId);
    }

    @Override
    public String toString() {
        return "CatalogoEPP{" +
                "eppId=" + eppId +
                ", nombre='" + nombre + '\'' +
                '}';
    }
}
