package com.sentinelmine.entity;

import com.sentinelmine.entity.enums.EstadoAlerta;
import com.sentinelmine.entity.enums.EstadoTurno;
import com.sentinelmine.entity.enums.TipoMovimiento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class EntityMappingTest {

    @Test
    @DisplayName("Debe validar la creación y estado de EventoTurno")
    void testEventoTurnoCreation() {
        Turno turno = new Turno("Guardia Día", LocalTime.of(7, 0), LocalTime.of(15, 30));
        turno.setTurnoId(1L);

        EventoTurno evento = new EventoTurno(turno, LocalDateTime.now());
        evento.setEventoId(100L);

        assertTrue(evento.isActivo());
        assertEquals(EstadoTurno.ACTIVO, evento.getEstado());
        assertEquals("Guardia Día", evento.getTurno().getNombre());
    }

    @Test
    @DisplayName("Debe validar la creación de MovimientoAforo y RolPersonal")
    void testMovimientoAforoCreation() {
        RolPersonal rol = new RolPersonal("Perforista", "Amarillo", true);
        rol.setRolId(2L);

        Turno turno = new Turno("Guardia Noche", LocalTime.of(23, 0), LocalTime.of(7, 30));
        EventoTurno evento = new EventoTurno(turno, LocalDateTime.now());

        MovimientoAforo movimiento = new MovimientoAforo(evento, rol, TipoMovimiento.ENTRADA, LocalDateTime.now());
        movimiento.setMovimientoId(500L);

        assertEquals(TipoMovimiento.ENTRADA, movimiento.getTipoMovimiento());
        assertEquals("Perforista", movimiento.getRol().getNombre());
        assertTrue(movimiento.getRol().getRequiereAforo());
    }

    @Test
    @DisplayName("Debe validar la creación y relaciones de FaltaEPP y CatalogoEPP")
    void testFaltaEPPCreation() {
        CatalogoEPP epp = new CatalogoEPP("Casco de Seguridad con Barbiquejo");
        epp.setEppId(1L);

        EventoTurno evento = new EventoTurno();
        FaltaEPP falta = new FaltaEPP(evento, null, epp, 0.95f, "https://snapshot.url/1", EstadoAlerta.PENDIENTE, LocalDateTime.now());
        falta.setFaltaId(10L);

        assertEquals(EstadoAlerta.PENDIENTE, falta.getEstadoAlerta());
        assertEquals("Casco de Seguridad con Barbiquejo", falta.getEpp().getNombre());
        assertEquals(0.95f, falta.getNivelConfianza());
    }

    @Test
    @DisplayName("Debe validar el balance de personal en CierreAuditoriaTurno")
    void testCierreAuditoriaTurno() {
        EventoTurno evento = new EventoTurno();
        int totalEntradas = 45;
        int totalSalidas = 43;
        int diferencia = totalEntradas - totalSalidas; // 2 personas remanentes en socavón

        CierreAuditoriaTurno cierre = new CierreAuditoriaTurno(evento, totalEntradas, totalSalidas, diferencia, LocalDateTime.now());
        cierre.setAuditoriaId(1L);

        assertEquals(45, cierre.getTotalEntradas());
        assertEquals(43, cierre.getTotalSalidas());
        assertEquals(2, cierre.getDiferencia());
    }
}
