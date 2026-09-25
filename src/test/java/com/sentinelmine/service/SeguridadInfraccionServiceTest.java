package com.sentinelmine.service;

import com.sentinelmine.dto.request.AnomaliaRequestDTO;
import com.sentinelmine.dto.request.FaltaEPPRequestDTO;
import com.sentinelmine.entity.*;
import com.sentinelmine.entity.enums.EstadoAlerta;
import com.sentinelmine.entity.enums.EstadoTurno;
import com.sentinelmine.entity.enums.TipoMovimiento;
import com.sentinelmine.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeguridadInfraccionServiceTest {

    @Mock
    private FaltaEPPRepository faltaEPPRepository;

    @Mock
    private AnomaliaMovimientoRepository anomaliaMovimientoRepository;

    @Mock
    private EventoTurnoRepository eventoTurnoRepository;

    @Mock
    private CatalogoEPPRepository catalogoEPPRepository;

    @Mock
    private CatalogoAnomaliasRepository catalogoAnomaliasRepository;

    @Mock
    private RolPersonalRepository rolPersonalRepository;

    @Mock
    private EventoTurnoService eventoTurnoService;

    @InjectMocks
    private SeguridadInfraccionService seguridadInfraccionService;

    private EventoTurno eventoActivo;
    private RolPersonal rolOperador;
    private CatalogoEPP eppCasco;
    private CatalogoAnomalias anomaliaSentido;

    @BeforeEach
    void setUp() {
        Turno turno = new Turno("Guardia Día", LocalTime.of(7, 0), LocalTime.of(15, 30));
        turno.setTurnoId(1L);

        eventoActivo = new EventoTurno(turno, LocalDateTime.now().minusHours(2));
        eventoActivo.setEventoId(20L);
        eventoActivo.setEstado(EstadoTurno.ACTIVO);

        rolOperador = new RolPersonal("Operador de Perforación", "Amarillo", true);
        rolOperador.setRolId(1L);

        eppCasco = new CatalogoEPP("Casco de Seguridad con Barbiquejo");
        eppCasco.setEppId(1L);

        anomaliaSentido = new CatalogoAnomalias("Cruce en Sentido Contrario al Flujo");
        anomaliaSentido.setCatalogoAnomaliaId(1L);
    }

    @Test
    @DisplayName("Debe registrar FaltaEPP con eventoId explícito, nivel de confianza, snapshot_url y estado PENDIENTE")
    void testRegistrarFaltaEPPConEventoExplicito() {
        FaltaEPPRequestDTO dto = new FaltaEPPRequestDTO(
                20L, 1L, 1L, 0.95f, "/evidencias/falta_casco_20.jpg", LocalDateTime.now()
        );

        when(eventoTurnoRepository.findById(20L)).thenReturn(Optional.of(eventoActivo));
        when(catalogoEPPRepository.findById(1L)).thenReturn(Optional.of(eppCasco));
        when(rolPersonalRepository.findById(1L)).thenReturn(Optional.of(rolOperador));
        when(faltaEPPRepository.save(any(FaltaEPP.class))).thenAnswer(invocation -> {
            FaltaEPP f = invocation.getArgument(0);
            f.setFaltaId(101L);
            return f;
        });

        FaltaEPP resultado = seguridadInfraccionService.registrarFaltaEPP(dto);

        assertNotNull(resultado);
        assertEquals(101L, resultado.getFaltaId());
        assertEquals(0.95f, resultado.getNivelConfianza());
        assertEquals("/evidencias/falta_casco_20.jpg", resultado.getSnapshotUrl());
        assertEquals(EstadoAlerta.PENDIENTE, resultado.getEstadoAlerta());
        assertEquals(eppCasco, resultado.getEpp());
        assertEquals(rolOperador, resultado.getRol());
        assertEquals(eventoActivo, resultado.getEventoTurno());
        verify(faltaEPPRepository, times(1)).save(any(FaltaEPP.class));
    }

    @Test
    @DisplayName("Debe registrar FaltaEPP resolviendo automáticamente el turno activo si eventoId es null")
    void testRegistrarFaltaEPPResolucionAutomaticaTurno() {
        FaltaEPPRequestDTO dto = new FaltaEPPRequestDTO(
                null, 1L, 1L, 0.91f, "/evidencias/epp_auto.jpg", LocalDateTime.now()
        );

        when(eventoTurnoService.resolverEventoActivoParaMovimiento(any(LocalDateTime.class), eq(TipoMovimiento.ENTRADA)))
                .thenReturn(eventoActivo);
        when(catalogoEPPRepository.findById(1L)).thenReturn(Optional.of(eppCasco));
        when(rolPersonalRepository.findById(1L)).thenReturn(Optional.of(rolOperador));
        when(faltaEPPRepository.save(any(FaltaEPP.class))).thenAnswer(invocation -> {
            FaltaEPP f = invocation.getArgument(0);
            f.setFaltaId(102L);
            return f;
        });

        FaltaEPP resultado = seguridadInfraccionService.registrarFaltaEPP(dto);

        assertNotNull(resultado);
        assertEquals(102L, resultado.getFaltaId());
        assertEquals(20L, resultado.getEventoTurno().getEventoId());
        verify(eventoTurnoService, times(1)).resolverEventoActivoParaMovimiento(any(LocalDateTime.class), eq(TipoMovimiento.ENTRADA));
        verify(faltaEPPRepository, times(1)).save(any(FaltaEPP.class));
    }

    @Test
    @DisplayName("Debe registrar AnomaliaMovimiento con nivel de confianza, snapshot_url y estado PENDIENTE")
    void testRegistrarAnomaliaMovimiento() {
        AnomaliaRequestDTO dto = new AnomaliaRequestDTO(
                20L, 1L, 1L, 0.88f, "/evidencias/anom_flujo.jpg", LocalDateTime.now()
        );

        when(eventoTurnoRepository.findById(20L)).thenReturn(Optional.of(eventoActivo));
        when(catalogoAnomaliasRepository.findById(1L)).thenReturn(Optional.of(anomaliaSentido));
        when(rolPersonalRepository.findById(1L)).thenReturn(Optional.of(rolOperador));
        when(anomaliaMovimientoRepository.save(any(AnomaliaMovimiento.class))).thenAnswer(invocation -> {
            AnomaliaMovimiento a = invocation.getArgument(0);
            a.setAnomaliaId(201L);
            return a;
        });

        AnomaliaMovimiento resultado = seguridadInfraccionService.registrarAnomalia(dto);

        assertNotNull(resultado);
        assertEquals(201L, resultado.getAnomaliaId());
        assertEquals(0.88f, resultado.getNivelConfianza());
        assertEquals("/evidencias/anom_flujo.jpg", resultado.getSnapshotUrl());
        assertEquals(EstadoAlerta.PENDIENTE, resultado.getEstadoAlerta());
        assertEquals(anomaliaSentido, resultado.getCatalogoAnomalia());
        verify(anomaliaMovimientoRepository, times(1)).save(any(AnomaliaMovimiento.class));
    }

    @Test
    @DisplayName("Debe permitir cambiar el estado de una falta de EPP a NOTIFICADA")
    void testCambiarEstadoFaltaEPP() {
        FaltaEPP falta = new FaltaEPP(eventoActivo, rolOperador, eppCasco, 0.95f, null, EstadoAlerta.PENDIENTE, LocalDateTime.now());
        falta.setFaltaId(105L);

        when(faltaEPPRepository.findById(105L)).thenReturn(Optional.of(falta));
        when(faltaEPPRepository.save(any(FaltaEPP.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FaltaEPP actualizada = seguridadInfraccionService.cambiarEstadoFaltaEPP(105L, EstadoAlerta.NOTIFICADA);

        assertNotNull(actualizada);
        assertEquals(EstadoAlerta.NOTIFICADA, actualizada.getEstadoAlerta());
        verify(faltaEPPRepository, times(1)).save(falta);
    }
}
