package com.sentinelmine.integration;

import com.sentinelmine.entity.EventoTurno;
import com.sentinelmine.entity.RolPersonal;
import com.sentinelmine.entity.enums.EstadoTurno;
import com.sentinelmine.repository.EventoTurnoRepository;
import com.sentinelmine.repository.MovimientoAforoRepository;
import com.sentinelmine.repository.RolPersonalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AforoApiIntegrationTest {

    private static final String API_KEY = "ci-test-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventoTurnoRepository eventoTurnoRepository;

    @Autowired
    private RolPersonalRepository rolPersonalRepository;

    @Autowired
    private MovimientoAforoRepository movimientoAforoRepository;

    @Test
    void apiRechazaClaveAusenteYPayloadInvalido() throws Exception {
        mockMvc.perform(get("/api/v1/aforo/tiempo-real"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/aforo/movimiento")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoMovimiento\":\"ENTRADA\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void movimientoRegistradoSePersisteYPuedeConsultarse() throws Exception {
        EventoTurno evento = eventoTurnoRepository.findByEstado(EstadoTurno.ACTIVO).get(0);
        RolPersonal rol = rolPersonalRepository.findAll().get(0);

        mockMvc.perform(post("/api/v1/aforo/movimiento")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventoId": %d,
                                  "rolId": %d,
                                  "tipoMovimiento": "ENTRADA",
                                  "trackId": 98123
                                }
                                """.formatted(evento.getEventoId(), rol.getRolId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoMovimiento").value("ENTRADA"))
                .andExpect(jsonPath("$.trackId").value(98123));

        assertThat(movimientoAforoRepository.findAll())
                .anySatisfy(movimiento -> {
                    assertThat(movimiento.getEventoTurno().getEventoId()).isEqualTo(evento.getEventoId());
                    assertThat(movimiento.getTrackId()).isEqualTo(98123);
                });

        mockMvc.perform(get("/api/v1/aforo/movimientos/recientes?limit=10")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].trackId").value(98123));
    }
}
