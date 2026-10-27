package com.sentinelmine.service;

import com.sentinelmine.entity.ConfiguracionAforo;
import com.sentinelmine.repository.ConfiguracionAforoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AforoServiceTest {

    @Mock
    private ConfiguracionAforoRepository configuracionAforoRepository;

    @InjectMocks
    private AforoService aforoService;

    @Test
    void actualizarLimites_persisteYAplicaEnCaliente() {
        aforoService.actualizarLimites(80, 5);

        verify(configuracionAforoRepository).save(any(ConfiguracionAforo.class));
        assertEquals(80, aforoService.getAforoMaximo());
        assertEquals(5, aforoService.getAforoMinimo());
    }

    @Test
    void actualizarLimites_rechazaValoresIncoherentes() {
        assertThrows(IllegalArgumentException.class, () -> aforoService.actualizarLimites(0, 0));
        assertThrows(IllegalArgumentException.class, () -> aforoService.actualizarLimites(10, -1));
        assertThrows(IllegalArgumentException.class, () -> aforoService.actualizarLimites(10, 11));
        verify(configuracionAforoRepository, never()).save(any());
    }

    @Test
    void cargarLimites_usaLosValoresGuardados() {
        when(configuracionAforoRepository.findById(ConfiguracionAforo.ID_UNICO))
                .thenReturn(Optional.of(new ConfiguracionAforo(120, 15)));

        aforoService.cargarLimites();

        assertEquals(120, aforoService.getAforoMaximo());
        assertEquals(15, aforoService.getAforoMinimo());
    }
}
