package com.sentinelmine.service;

import com.sentinelmine.dto.response.CierreTurnoResponseDTO;
import com.sentinelmine.entity.CierreAuditoriaTurno;
import com.sentinelmine.entity.EventoTurno;
import com.sentinelmine.entity.Turno;
import com.sentinelmine.entity.enums.EstadoTurno;
import com.sentinelmine.entity.enums.TipoMovimiento;
import com.sentinelmine.repository.CierreAuditoriaTurnoRepository;
import com.sentinelmine.repository.EventoTurnoRepository;
import com.sentinelmine.repository.MovimientoAforoRepository;
import com.sentinelmine.repository.TurnoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventoTurnoServiceTest {

    @Mock
    private EventoTurnoRepository eventoTurnoRepository;

    @Mock
    private TurnoRepository turnoRepository;

    @Mock
    private MovimientoAforoRepository movimientoAforoRepository;

    @Mock
    private CierreAuditoriaTurnoRepository cierreAuditoriaTurnoRepository;

    @InjectMocks
    private EventoTurnoService eventoTurnoService;

    private Turno turnoDia;
    private Turno turnoTarde;

    @BeforeEach
    void setUp() {
        turnoDia = new Turno("Turno Guardia Día", LocalTime.of(7, 0), LocalTime.of(15, 30));
        turnoDia.setTurnoId(1L);

        turnoTarde = new Turno("Turno Guardia Tarde", LocalTime.of(15, 0), LocalTime.of(23, 30));
        turnoTarde.setTurnoId(2L);
    }

    // =========================================================================
    // REQUERIMIENTO 1: GESTIÓN DE TURNOS SIMULTÁNEOS (SOLAPAMIENTO)
    // =========================================================================

    @Test
    @DisplayName("Debe abrir un nuevo turno operativo en estado ACTIVO sin afectar turnos previos")
    void testAbrirTurnoExitoso() {
        when(turnoRepository.findById(1L)).thenReturn(Optional.of(turnoDia));
        when(eventoTurnoRepository.save(any(EventoTurno.class))).thenAnswer(invocation -> {
            EventoTurno e = invocation.getArgument(0);
            e.setEventoId(10L);
            return e;
        });

        EventoTurno creado = eventoTurnoService.abrirTurno(1L, LocalDateTime.of(2026, 9, 24, 7, 0));

        assertNotNull(creado);
        assertEquals(10L, creado.getEventoId());
        assertEquals(EstadoTurno.ACTIVO, creado.getEstado());
        assertEquals(turnoDia, creado.getTurno());
        verify(eventoTurnoRepository, times(1)).save(any(EventoTurno.class));
    }

    @Test
    @DisplayName("Debe lanzar excepción si el turnoId no existe al abrir turno")
    void testAbrirTurnoInexistenteLanzaExcepcion() {
        when(turnoRepository.findById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                eventoTurnoService.abrirTurno(99L, LocalDateTime.now()));

        assertTrue(ex.getMessage().contains("No se encontró el turno"));
        verify(eventoTurnoRepository, never()).save(any(EventoTurno.class));
    }

    @Test
    @DisplayName("Debe resolver evento activo durante solapamiento: ENTRADA -> turno nuevo, SALIDA -> turno saliente")
    void testResolverEventoActivoDuranteSolapamiento() {
        EventoTurno eventoDia = new EventoTurno(turnoDia, LocalDateTime.of(2026, 9, 24, 7, 0));
        eventoDia.setEventoId(101L);
        eventoDia.setEstado(EstadoTurno.ACTIVO);

        EventoTurno eventoTarde = new EventoTurno(turnoTarde, LocalDateTime.of(2026, 9, 24, 15, 0));
        eventoTarde.setEventoId(102L);
        eventoTarde.setEstado(EstadoTurno.ACTIVO);

        // Repositorio retorna ordenado por fechaInicio DESC (eventoTarde primero, eventoDia después)
        when(eventoTurnoRepository.findAllByEstadoWithTurno(EstadoTurno.ACTIVO))
                .thenReturn(List.of(eventoTarde, eventoDia));

        // 1. Ingreso de personal durante solapamiento -> pertenece a la guardia entrante (Guardia Tarde #102)
        EventoTurno paraEntrada = eventoTurnoService.resolverEventoActivoParaMovimiento(LocalDateTime.now(), TipoMovimiento.ENTRADA);
        assertEquals(102L, paraEntrada.getEventoId());
        assertEquals("Turno Guardia Tarde", paraEntrada.getTurno().getNombre());

        // 2. Salida de personal durante solapamiento -> pertenece a la guardia saliente (Guardia Día #101)
        EventoTurno paraSalida = eventoTurnoService.resolverEventoActivoParaMovimiento(LocalDateTime.now(), TipoMovimiento.SALIDA);
        assertEquals(101L, paraSalida.getEventoId());
        assertEquals("Turno Guardia Día", paraSalida.getTurno().getNombre());
    }

    @Test
    @DisplayName("Debe indicar correctamente si hay solapamiento operativo de turnos")
    void testHaySolapamientoTurnos() {
        EventoTurno e1 = new EventoTurno(turnoDia, LocalDateTime.now().minusHours(8));
        EventoTurno e2 = new EventoTurno(turnoTarde, LocalDateTime.now().minusMinutes(20));

        when(eventoTurnoRepository.findAllByEstadoWithTurno(EstadoTurno.ACTIVO))
                .thenReturn(List.of(e2, e1));

        assertTrue(eventoTurnoService.haySolapamientoTurnos());
        assertEquals("Solapamiento: Turno Guardia Tarde + Turno Guardia Día", eventoTurnoService.obtenerDescripcionTurnosActivos());
    }

    // =========================================================================
    // REQUERIMIENTO 2: CIERRE Y AUDITORÍA DE TURNO
    // =========================================================================

    @Test
    @DisplayName("Debe cerrar turno consolidando únicamente movimientos con requiere_aforo = true")
    void testCerrarTurnoConsolidaSoloRolesConRequiereAforo() {
        Long eventoId = 50L;
        EventoTurno evento = new EventoTurno(turnoDia, LocalDateTime.now().minusHours(8));
        evento.setEventoId(eventoId);
        evento.setEstado(EstadoTurno.ACTIVO);

        when(eventoTurnoRepository.findByIdWithTurno(eventoId)).thenReturn(Optional.of(evento));
        when(cierreAuditoriaTurnoRepository.existsByEventoTurno_EventoId(eventoId)).thenReturn(false);

        // Simulamos que el conteo con requiere_aforo=true da: 40 entradas, 38 salidas
        when(movimientoAforoRepository.countByEventoTurno_EventoIdAndTipoMovimientoAndRol_RequiereAforoTrue(eventoId, TipoMovimiento.ENTRADA))
                .thenReturn(40L);
        when(movimientoAforoRepository.countByEventoTurno_EventoIdAndTipoMovimientoAndRol_RequiereAforoTrue(eventoId, TipoMovimiento.SALIDA))
                .thenReturn(38L);

        when(cierreAuditoriaTurnoRepository.save(any(CierreAuditoriaTurno.class))).thenAnswer(invocation -> {
            CierreAuditoriaTurno c = invocation.getArgument(0);
            c.setAuditoriaId(1001L);
            return c;
        });

        CierreTurnoResponseDTO resultado = eventoTurnoService.cerrarTurno(eventoId);

        // Validaciones del cierre
        assertNotNull(resultado);
        assertEquals(1001L, resultado.getAuditoriaId());
        assertEquals(eventoId, resultado.getEventoId());
        assertEquals("Turno Guardia Día", resultado.getNombreTurno());
        assertEquals(40, resultado.getTotalEntradas());
        assertEquals(38, resultado.getTotalSalidas());
        assertEquals(2, resultado.getDiferencia()); // 2 personas remanentes
        assertTrue(resultado.getMensajeAuditoria().contains("Quedan 2 personas"));

        // Validar que el estado del EventoTurno se actualizó a CERRADO y fechaFin no es nula
        assertEquals(EstadoTurno.CERRADO, evento.getEstado());
        assertNotNull(evento.getFechaFin());

        // Validar persistencia atómica
        verify(cierreAuditoriaTurnoRepository, times(1)).save(any(CierreAuditoriaTurno.class));
        verify(eventoTurnoRepository, times(1)).save(evento);

        // Validar que se llamó específicamente al método filtrado por requiere_aforo = true
        verify(movimientoAforoRepository, times(1))
                .countByEventoTurno_EventoIdAndTipoMovimientoAndRol_RequiereAforoTrue(eventoId, TipoMovimiento.ENTRADA);
        verify(movimientoAforoRepository, times(1))
                .countByEventoTurno_EventoIdAndTipoMovimientoAndRol_RequiereAforoTrue(eventoId, TipoMovimiento.SALIDA);
    }

    @Test
    @DisplayName("Debe reportar balance cuadrado cuando total_entradas == total_salidas")
    void testCerrarTurnoBalanceCuadrado() {
        Long eventoId = 51L;
        EventoTurno evento = new EventoTurno(turnoDia, LocalDateTime.now().minusHours(8));
        evento.setEventoId(eventoId);
        evento.setEstado(EstadoTurno.ACTIVO);

        when(eventoTurnoRepository.findByIdWithTurno(eventoId)).thenReturn(Optional.of(evento));
        when(cierreAuditoriaTurnoRepository.existsByEventoTurno_EventoId(eventoId)).thenReturn(false);
        when(movimientoAforoRepository.countByEventoTurno_EventoIdAndTipoMovimientoAndRol_RequiereAforoTrue(eventoId, TipoMovimiento.ENTRADA))
                .thenReturn(50L);
        when(movimientoAforoRepository.countByEventoTurno_EventoIdAndTipoMovimientoAndRol_RequiereAforoTrue(eventoId, TipoMovimiento.SALIDA))
                .thenReturn(50L);

        when(cierreAuditoriaTurnoRepository.save(any(CierreAuditoriaTurno.class))).thenAnswer(invocation -> {
            CierreAuditoriaTurno c = invocation.getArgument(0);
            c.setAuditoriaId(1002L);
            return c;
        });

        CierreTurnoResponseDTO resultado = eventoTurnoService.cerrarTurno(eventoId);

        assertEquals(0, resultado.getDiferencia());
        assertTrue(resultado.getMensajeAuditoria().contains("Balance cuadrado de personal"));
    }

    @Test
    @DisplayName("Debe lanzar excepción si se intenta cerrar un turno que no está ACTIVO")
    void testCerrarTurnoNoActivoLanzaExcepcion() {
        Long eventoId = 52L;
        EventoTurno evento = new EventoTurno(turnoDia, LocalDateTime.now().minusHours(8));
        evento.setEventoId(eventoId);
        evento.setEstado(EstadoTurno.CERRADO); // Ya cerrado

        when(eventoTurnoRepository.findByIdWithTurno(eventoId)).thenReturn(Optional.of(evento));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                eventoTurnoService.cerrarTurno(eventoId));

        assertTrue(ex.getMessage().contains("ya no está ACTIVO"));
        verify(cierreAuditoriaTurnoRepository, never()).save(any(CierreAuditoriaTurno.class));
    }

    @Test
    @DisplayName("Debe lanzar excepción si el evento de turno ya tiene auditoría previa")
    void testCerrarTurnoConAuditoriaPreviaLanzaExcepcion() {
        Long eventoId = 53L;
        EventoTurno evento = new EventoTurno(turnoDia, LocalDateTime.now().minusHours(8));
        evento.setEventoId(eventoId);
        evento.setEstado(EstadoTurno.ACTIVO);

        when(eventoTurnoRepository.findByIdWithTurno(eventoId)).thenReturn(Optional.of(evento));
        when(cierreAuditoriaTurnoRepository.existsByEventoTurno_EventoId(eventoId)).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                eventoTurnoService.cerrarTurno(eventoId));

        assertTrue(ex.getMessage().contains("ya cuenta con un registro de auditoría"));
        verify(cierreAuditoriaTurnoRepository, never()).save(any(CierreAuditoriaTurno.class));
    }
}
