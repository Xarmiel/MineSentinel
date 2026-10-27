package com.sentinelmine.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Límites de aforo editables por el administrador. Fila única (id = 1): sin
 * ella rigen los valores de application.properties.
 */
@Entity
@Table(name = "configuracion_aforo")
public class ConfiguracionAforo {

    public static final Long ID_UNICO = 1L;

    @Id
    @Column(name = "configuracion_id")
    private Long configuracionId = ID_UNICO;

    @Column(name = "aforo_maximo", nullable = false)
    private int aforoMaximo;

    @Column(name = "aforo_minimo", nullable = false)
    private int aforoMinimo;

    public ConfiguracionAforo() {
    }

    public ConfiguracionAforo(int aforoMaximo, int aforoMinimo) {
        this.aforoMaximo = aforoMaximo;
        this.aforoMinimo = aforoMinimo;
    }

    public Long getConfiguracionId() {
        return configuracionId;
    }

    public int getAforoMaximo() {
        return aforoMaximo;
    }

    public int getAforoMinimo() {
        return aforoMinimo;
    }
}
