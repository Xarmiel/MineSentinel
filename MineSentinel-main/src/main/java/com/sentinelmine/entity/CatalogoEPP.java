package com.sentinelmine.entity;

import com.sentinelmine.entity.enums.TipoCatalogoEPP;
import jakarta.persistence.*;
import java.util.Objects;

/**
 * 3. CatalogoEPP: Elementos de Protección Personal monitoreados por YOLOv8 (e.g. Casco, Chaleco Reflectivo, Lámpara Minera, Barbiquejo, Botas).
 *
 * `tipo` distingue el EPP homologado (CONFORME) del objeto no apto que intenta
 * sustituirlo (IMPOSTOR: gorra, gafas de sol, zapatillas).
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

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20,
            columnDefinition = "varchar(20) default 'CONFORME' not null")
    private TipoCatalogoEPP tipo = TipoCatalogoEPP.CONFORME;

    public CatalogoEPP() {
    }

    public CatalogoEPP(String nombre) {
        this.nombre = nombre;
        this.tipo = TipoCatalogoEPP.CONFORME;
    }

    public CatalogoEPP(String nombre, TipoCatalogoEPP tipo) {
        this.nombre = nombre;
        this.tipo = tipo != null ? tipo : TipoCatalogoEPP.CONFORME;
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

    public TipoCatalogoEPP getTipo() {
        return tipo;
    }

    public void setTipo(TipoCatalogoEPP tipo) {
        this.tipo = tipo;
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
                ", tipo=" + tipo +
                '}';
    }
}
