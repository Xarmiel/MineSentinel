package com.sentinelmine.service;

import com.sentinelmine.dto.request.AnomaliaRequestDTO;
import com.sentinelmine.dto.request.FaltaEPPRequestDTO;
import com.sentinelmine.dto.request.InfraccionResolucionRequestDTO;
import com.sentinelmine.entity.*;
import com.sentinelmine.entity.enums.EstadoAlerta;
import com.sentinelmine.entity.enums.EstadoTurno;
import com.sentinelmine.entity.enums.TipoCatalogoEPP;
import com.sentinelmine.entity.enums.TipoInfraccionEPP;
import com.sentinelmine.entity.enums.TipoMovimiento;
import com.sentinelmine.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    @Mock
    private SseNotificationService sseNotificationService;

    @Mock
    private MovimientoAforoRepository movimientoAforoRepository;

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

    // =========================================================================
    // RESOLUCIÓN AUTOMÁTICA (el motor detecta que el trabajador se puso el EPP)
    // =========================================================================

    @Test
    @DisplayName("Debe marcar la falta como RESUELTA y reemitir 'alerta-actualizada' al resolver")
    void testResolverFaltaEPPMarcaResueltaYEmiteEvento() {
        FaltaEPP falta = new FaltaEPP(eventoActivo, rolOperador, eppCasco, 0.95f, null,
                EstadoAlerta.PENDIENTE, LocalDateTime.now(), 7, TipoInfraccionEPP.FALTANTE, "Sin Casco");
        falta.setFaltaId(110L);

        InfraccionResolucionRequestDTO dto = new InfraccionResolucionRequestDTO(
                110L, 7, "EPP repuesto: Casco de Seguridad", "/evidencias/cumple_7.jpg");

        when(faltaEPPRepository.findByIdWithDetails(110L)).thenReturn(Optional.of(falta));
        when(faltaEPPRepository.save(any(FaltaEPP.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FaltaEPP resultado = seguridadInfraccionService.resolverFaltaEPP(dto);

        assertEquals(EstadoAlerta.RESUELTA, resultado.getEstadoAlerta());
        assertEquals("/evidencias/cumple_7.jpg", resultado.getSnapshotUrl());
        verify(faltaEPPRepository, times(1)).save(falta);
        verify(sseNotificationService, times(1)).emitirEvento(eq("alerta-actualizada"), any(FaltaEPP.class));
    }

    @Test
    @DisplayName("Resolver una falta ya RESUELTA es idempotente: no vuelve a guardar ni emitir")
    void testResolverFaltaEPPIdempotente() {
        FaltaEPP falta = new FaltaEPP(eventoActivo, rolOperador, eppCasco, 0.95f, null,
                EstadoAlerta.RESUELTA, LocalDateTime.now(), 7, TipoInfraccionEPP.FALTANTE, "Sin Casco");
        falta.setFaltaId(111L);

        when(faltaEPPRepository.findByIdWithDetails(111L)).thenReturn(Optional.of(falta));

        FaltaEPP resultado = seguridadInfraccionService.resolverFaltaEPP(
                new InfraccionResolucionRequestDTO(111L, 7, null, null));

        assertEquals(EstadoAlerta.RESUELTA, resultado.getEstadoAlerta());
        verify(faltaEPPRepository, never()).save(any(FaltaEPP.class));
        verify(sseNotificationService, never()).emitirEvento(anyString(), any());
    }

    @Test
    @DisplayName("Debe lanzar error al resolver una falta inexistente")
    void testResolverFaltaEPPInexistente() {
        when(faltaEPPRepository.findByIdWithDetails(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                seguridadInfraccionService.resolverFaltaEPP(
                        new InfraccionResolucionRequestDTO(999L, null, null, null)));
        verify(faltaEPPRepository, never()).save(any(FaltaEPP.class));
    }

    // =========================================================================
    // INFRACCIONES DE TIPO IMPOSTOR (objetos no homologados que imitan al EPP)
    // =========================================================================

    @Test
    @DisplayName("Debe registrar una infracción IMPOSTOR con trackId y descripción, sin alterar el estado PENDIENTE")
    void testRegistrarInfraccionImpostor() {
        CatalogoEPP eppImpostor = new CatalogoEPP("Gorra / Sombrero No Apto como Casco", TipoCatalogoEPP.IMPOSTOR);
        eppImpostor.setEppId(8L);

        FaltaEPPRequestDTO dto = new FaltaEPPRequestDTO(
                20L, 1L, 8L, 0.93f, "/evidencias/gorra_7.jpg", LocalDateTime.now(),
                7, TipoInfraccionEPP.IMPOSTOR, "Se porta un tocado no certificado en lugar del casco"
        );

        when(eventoTurnoRepository.findById(20L)).thenReturn(Optional.of(eventoActivo));
        when(catalogoEPPRepository.findById(8L)).thenReturn(Optional.of(eppImpostor));
        when(rolPersonalRepository.findById(1L)).thenReturn(Optional.of(rolOperador));
        when(faltaEPPRepository.save(any(FaltaEPP.class))).thenAnswer(invocation -> {
            FaltaEPP f = invocation.getArgument(0);
            f.setFaltaId(106L);
            return f;
        });

        FaltaEPP resultado = seguridadInfraccionService.registrarFaltaEPP(dto);

        assertNotNull(resultado);
        assertEquals(106L, resultado.getFaltaId());
        assertEquals(TipoInfraccionEPP.IMPOSTOR, resultado.getTipoInfraccion());
        assertEquals(7, resultado.getTrackId());
        assertEquals("Se porta un tocado no certificado en lugar del casco", resultado.getDescripcion());
        assertEquals(eppImpostor, resultado.getEpp());
        assertEquals(EstadoAlerta.PENDIENTE, resultado.getEstadoAlerta());
    }

    @Test
    @DisplayName("Debe asumir FALTANTE cuando el cliente no envía tipoInfraccion (compatibilidad con el cliente legacy)")
    void testRegistrarInfraccionSinTipoAsumeFaltante() {
        FaltaEPPRequestDTO dto = new FaltaEPPRequestDTO(
                20L, 1L, 1L, 0.88f, "/evidencias/sin_casco.jpg", LocalDateTime.now()
        );

        when(eventoTurnoRepository.findById(20L)).thenReturn(Optional.of(eventoActivo));
        when(catalogoEPPRepository.findById(1L)).thenReturn(Optional.of(eppCasco));
        when(rolPersonalRepository.findById(1L)).thenReturn(Optional.of(rolOperador));
        when(faltaEPPRepository.save(any(FaltaEPP.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FaltaEPP resultado = seguridadInfraccionService.registrarFaltaEPP(dto);

        assertEquals(TipoInfraccionEPP.FALTANTE, resultado.getTipoInfraccion());
        assertNull(resultado.getTrackId());
        assertNull(resultado.getDescripcion());
    }

    @Test
    @DisplayName("Debe degradar a FALTANTE si el cliente reporta IMPOSTOR sobre un elemento CONFORME del catálogo")
    void testRegistrarInfraccionImpostorSobreEppConformeSeCorrige() {
        // eppCasco es un elemento CONFORME: un impostor nunca debe quedar registrado como tal.
        FaltaEPPRequestDTO dto = new FaltaEPPRequestDTO(
                20L, 1L, 1L, 0.97f, "/evidencias/error_clasificacion.jpg", LocalDateTime.now(),
                12, TipoInfraccionEPP.IMPOSTOR, "Clasificación inconsistente del cliente"
        );

        when(eventoTurnoRepository.findById(20L)).thenReturn(Optional.of(eventoActivo));
        when(catalogoEPPRepository.findById(1L)).thenReturn(Optional.of(eppCasco));
        when(rolPersonalRepository.findById(1L)).thenReturn(Optional.of(rolOperador));
        when(faltaEPPRepository.save(any(FaltaEPP.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FaltaEPP resultado = seguridadInfraccionService.registrarFaltaEPP(dto);

        assertEquals(TipoInfraccionEPP.FALTANTE, resultado.getTipoInfraccion());
        assertEquals(12, resultado.getTrackId());
    }

    @Test
    @DisplayName("Debe filtrar las infracciones recientes por tipo cuando se solicita sólo un tipo")
    void testObtenerUltimasFaltasEPPPorTipo() {
        FaltaEPP impostor = new FaltaEPP(eventoActivo, rolOperador, eppCasco, 0.9f, null,
                EstadoAlerta.PENDIENTE, LocalDateTime.now(), 9, TipoInfraccionEPP.IMPOSTOR, null);
        impostor.setFaltaId(107L);

        when(faltaEPPRepository.findByTipoInfraccion(any(Pageable.class), eq(TipoInfraccionEPP.IMPOSTOR)))
                .thenReturn(List.of(impostor));

        List<FaltaEPP> resultado = seguridadInfraccionService.obtenerUltimasFaltasEPPPorTipo(TipoInfraccionEPP.IMPOSTOR, 5);

        assertEquals(1, resultado.size());
        assertEquals(TipoInfraccionEPP.IMPOSTOR, resultado.get(0).getTipoInfraccion());
        verify(faltaEPPRepository, times(1)).findByTipoInfraccion(any(Pageable.class), eq(TipoInfraccionEPP.IMPOSTOR));
    }

    @Test
    @DisplayName("KPIs del turno: cumplimiento cuenta personas con falta sin entrada y toma el EPP más incumplido")
    void obtenerKpisTurnoActivo_calculaCumplimientoYEppMasIncumplido() {
        // 4 entradas (1..4); faltas en 2 y en 5 (detectado sin cruzar la línea) -> 2 de 5 con falta = 60%
        when(movimientoAforoRepository.trackIdsConEntradaTurnoActivo()).thenReturn(List.of(1, 2, 3, 4));
        when(faltaEPPRepository.trackIdsConFaltaTurnoActivo()).thenReturn(List.of(2, 5));
        when(faltaEPPRepository.contarInfraccionesTurnoActivo()).thenReturn(7L);
        when(faltaEPPRepository.contarInfraccionesPorEppTurnoActivo())
                .thenReturn(List.of(new Object[]{"Casco", 5L}, new Object[]{"Guantes", 2L}));

        var kpis = seguridadInfraccionService.obtenerKpisTurnoActivo();

        assertEquals(60, kpis.get("cumplimientoPorcentaje"));
        assertEquals(5, kpis.get("personasDetectadas"));
        assertEquals(2, kpis.get("personasConFalta"));
        assertEquals(7L, kpis.get("infracciones"));
        assertEquals("Casco", kpis.get("eppMasIncumplido"));
        assertEquals(5L, kpis.get("eppMasIncumplidoConteo"));
    }

    @Test
    @DisplayName("KPIs del turno: sin personas detectadas el cumplimiento es 100% y no hay EPP más incumplido")
    void obtenerKpisTurnoActivo_sinDatos() {
        when(movimientoAforoRepository.trackIdsConEntradaTurnoActivo()).thenReturn(List.of());
        when(faltaEPPRepository.trackIdsConFaltaTurnoActivo()).thenReturn(List.of());
        when(faltaEPPRepository.contarInfraccionesPorEppTurnoActivo()).thenReturn(List.of());

        var kpis = seguridadInfraccionService.obtenerKpisTurnoActivo();

        assertEquals(100, kpis.get("cumplimientoPorcentaje"));
        assertNull(kpis.get("eppMasIncumplido"));
        assertEquals(0L, kpis.get("eppMasIncumplidoConteo"));
    }
}
