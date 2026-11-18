package com.sentinelmine.integration;

import com.sentinelmine.entity.EventoTurno;
import com.sentinelmine.entity.Turno;
import com.sentinelmine.entity.enums.EstadoTurno;
import com.sentinelmine.repository.CierreAuditoriaTurnoRepository;
import com.sentinelmine.repository.EventoTurnoRepository;
import com.sentinelmine.repository.TurnoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TurnoApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TurnoRepository turnoRepository;

    @Autowired
    private EventoTurnoRepository eventoTurnoRepository;

    @Autowired
    private CierreAuditoriaTurnoRepository cierreAuditoriaTurnoRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void turnoPuedeAbrirseCerrarseYGenerarAuditoriaPersistida() throws Exception {
        Turno turno = turnoRepository.save(
                new Turno("Turno de integración", LocalTime.of(8, 0), LocalTime.of(16, 0)));

        MvcResult apertura = mockMvc.perform(post("/api/v1/turnos/abrir")
                        .header("X-API-Key", "ci-test-key")
                        .param("turnoId", turno.getTurnoId().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andReturn();

        long eventoId = objectMapper.readTree(apertura.getResponse().getContentAsString())
                .get("eventoId").asLong();

        mockMvc.perform(post("/api/v1/turnos/{eventoId}/cerrar", eventoId)
                        .header("X-API-Key", "ci-test-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(eventoId));

        EventoTurno eventoCerrado = eventoTurnoRepository.findById(eventoId).orElseThrow();
        assertThat(eventoCerrado.getEstado()).isEqualTo(EstadoTurno.CERRADO);
        assertThat(cierreAuditoriaTurnoRepository.existsByEventoTurno_EventoId(eventoId)).isTrue();
    }
}
