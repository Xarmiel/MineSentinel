package com.sentinelmine.service;

import com.sentinelmine.dto.request.MovimientoRequestDTO;
import com.sentinelmine.dto.response.AforoGlobalResponseDTO;
import com.sentinelmine.entity.EventoTurno;
import com.sentinelmine.entity.MovimientoAforo;
import com.sentinelmine.entity.RolPersonal;
import com.sentinelmine.entity.Turno;
import com.sentinelmine.entity.enums.EstadoTurno;
import com.sentinelmine.entity.enums.TipoMovimiento;
import com.sentinelmine.repository.EventoTurnoRepository;
import com.sentinelmine.repository.MovimientoAforoRepository;
import com.sentinelmine.repository.RolPersonalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MovimientoAforoServiceTest {

    @Mock
    private MovimientoAforoRepository movimientoAforoRepository;

    @Mock
    private EventoTurnoRepository eventoTurnoRepository;

    @Mock
    private RolPersonalRepository rolPersonalRepository;

    @Mock
    private EventoTurnoService eventoTurnoService;

    @InjectMocks
    private MovimientoAforoService movimientoAforoService;

    private EventoTurno eventoActivo;
    private RolPersonal rolPerforista;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(movimientoAforoService, "aforoMaximo", 50);
        ReflectionTestUtils.setField(movimientoAforoService, "umbralCriticoPorcentaje", 90);

        Turno turno = new Turno("Guardia Día", LocalTime.of(7, 0), LocalTime.of(15, 30));
        turno.setTurnoId(1L);

        eventoActivo = new EventoTurno(turno, LocalDateTime.now().minusHours(2));
        eventoActivo.setEventoId(10L);
        eventoActivo.setEstado(EstadoTurno.ACTIVO);

        rolPerforista = new RolPersonal("Operador de Perforación", "Amarillo", true);
        rolPerforista.setRolId(100L);
    }

    @Test
    @DisplayName("Debe registrar movimiento con eventoId explícito en turno activo")
    void testRegistrarMovimientoConEventoIdExplicito() {
        MovimientoRequestDTO dto = new MovimientoRequestDTO(10L, 100L, TipoMovimiento.ENTRADA, LocalDateTime.now());

        when(eventoTurnoRepository.findById(10L)).thenReturn(Optional.of(eventoActivo));
        when(rolPersonalRepository.findById(100L)).thenReturn(Optional.of(rolPerforista));
        when(movimientoAforoRepository.save(any(MovimientoAforo.class))).thenAnswer(invocation -> {
            MovimientoAforo m = invocation.getArgument(0);
            m.setMovimientoId(501L);
            return m;
        });

        MovimientoAforo guardado = movimientoAforoService.registrarMovimiento(dto);

        assertNotNull(guardado);
        assertEquals(501L, guardado.getMovimientoId());
        assertEquals(TipoMovimiento.ENTRADA, guardado.getTipoMovimiento());
        assertEquals(10L, guardado.getEventoTurno().getEventoId());
        assertEquals("Operador de Perforación", guardado.getRol().getNombre());
        verify(movimientoAforoRepository, times(1)).save(any(MovimientoAforo.class));
    }

    @Test
    @DisplayName("Debe resolver automáticamente el turno activo si eventoId es null (Solapamiento)")
    void testRegistrarMovimientoConResolucionAutomaticaTurno() {
        MovimientoRequestDTO dto = new MovimientoRequestDTO(null, 100L, TipoMovimiento.ENTRADA, LocalDateTime.now());

        when(eventoTurnoService.resolverEventoActivoParaMovimiento(any(LocalDateTime.class), eq(TipoMovimiento.ENTRADA)))
                .thenReturn(eventoActivo);
        when(rolPersonalRepository.findById(100L)).thenReturn(Optional.of(rolPerforista));
        when(movimientoAforoRepository.save(any(MovimientoAforo.class))).thenAnswer(invocation -> {
            MovimientoAforo m = invocation.getArgument(0);
            m.setMovimientoId(502L);
            return m;
        });

        MovimientoAforo guardado = movimientoAforoService.registrarMovimiento(dto);

        assertNotNull(guardado);
        assertEquals(502L, guardado.getMovimientoId());
        assertEquals(10L, guardado.getEventoTurno().getEventoId());
        verify(eventoTurnoService, times(1)).resolverEventoActivoParaMovimiento(any(LocalDateTime.class), eq(TipoMovimiento.ENTRADA));
        verify(movimientoAforoRepository, times(1)).save(any(MovimientoAforo.class));
    }

    @Test
    @DisplayName("Debe rechazar el registro de movimiento en un turno que no esté ACTIVO")
    void testRegistrarMovimientoEnTurnoInactivoLanzaExcepcion() {
        eventoActivo.setEstado(EstadoTurno.CERRADO);
        when(eventoTurnoRepository.findById(10L)).thenReturn(Optional.of(eventoActivo));

        MovimientoRequestDTO dto = new MovimientoRequestDTO(10L, 100L, TipoMovimiento.ENTRADA, LocalDateTime.now());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                movimientoAforoService.registrarMovimiento(dto));

        assertTrue(ex.getMessage().contains("No se pueden registrar movimientos en un turno inactivo"));
        verify(movimientoAforoRepository, never()).save(any(MovimientoAforo.class));
    }

    @Test
    @DisplayName("Debe calcular métricas de aforo global sumando turnos activos en solapamiento")
    void testObtenerAforoGlobalEnTiempoReal() {
        when(movimientoAforoRepository.calcularAforoNetoGlobalActivo()).thenReturn(35);
        when(eventoTurnoRepository.findByEstado(EstadoTurno.ACTIVO)).thenReturn(List.of(eventoActivo));
        when(movimientoAforoRepository.findAforoPorRolActivo()).thenReturn(List.of(
                new Object[]{"Operador de Perforación", "Amarillo", 25L},
                new Object[]{"Supervisor de Seguridad", "Blanco", 10L}
        ));

        AforoGlobalResponseDTO response = movimientoAforoService.obtenerAforoGlobalEnTiempoReal();

        assertNotNull(response);
        assertEquals(35, response.getAforoActual());
        assertEquals(50, response.getAforoMaximo());
        assertEquals(70, response.getPorcentajeCapacidad()); // (35/50) * 100 = 70%
        assertEquals("AFORO NORMAL", response.getEstadoAforo());
        assertEquals(1, response.getCantidadTurnosActivos());
        assertEquals(25, response.getConteoPorRol().get("Operador de Perforación"));
        assertEquals(10, response.getConteoPorRol().get("Supervisor de Seguridad"));
    }
}
