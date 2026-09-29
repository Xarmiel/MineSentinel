package com.sentinelmine.entity;

import jakarta.persistence.*;
import java.util.Objects;

/**
 * 4. CatalogoAnomalias: Tipos de conductas u ocurrencias anómalas detectadas en el flujo de personal (e.g. Cruce en sentido contrario, Movimiento en zona prohibida, Aglomeración en boca-mina, Caída a nivel).
 */
@Entity
@Table(name = "catalogo_anomalias")
public class CatalogoAnomalias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "catalogo_anomalia_id")
    private Long catalogoAnomaliaId;

    @Column(name = "nombre", nullable = false, length = 100, unique = true)
    private String nombre;

    public CatalogoAnomalias() {
    }

    public CatalogoAnomalias(String nombre) {
        this.nombre = nombre;
    }

    public Long getCatalogoAnomaliaId() {
        return catalogoAnomaliaId;
    }

    public void setCatalogoAnomaliaId(Long catalogoAnomaliaId) {
        this.catalogoAnomaliaId = catalogoAnomaliaId;
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
        CatalogoAnomalias that = (CatalogoAnomalias) o;
        return Objects.equals(catalogoAnomaliaId, that.catalogoAnomaliaId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(catalogoAnomaliaId);
    }

    @Override
    public String toString() {
        return "CatalogoAnomalias{" +
                "catalogoAnomaliaId=" + catalogoAnomaliaId +
                ", nombre='" + nombre + '\'' +
                '}';
    }
}
