package com.sentinelmine.controller;

import com.sentinelmine.dto.request.CuadroEPPRequestDTO;
import com.sentinelmine.service.CuadroEppService;
import com.sentinelmine.service.SseNotificationService;
import com.sentinelmine.service.VisionMarcoService;
import com.sentinelmine.service.VisionStreamService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/vision")
@CrossOrigin(origins = "*")
public class VisionRestController {

    private final CuadroEppService cuadroEppService;
    private final SseNotificationService sseNotificationService;
    private final VisionMarcoService visionMarcoService;
    private final VisionStreamService visionStreamService;

    public VisionRestController(CuadroEppService cuadroEppService,
                                SseNotificationService sseNotificationService,
                                VisionMarcoService visionMarcoService,
                                VisionStreamService visionStreamService) {
        this.cuadroEppService = cuadroEppService;
        this.sseNotificationService = sseNotificationService;
        this.visionMarcoService = visionMarcoService;
        this.visionStreamService = visionStreamService;
    }

    @PostMapping("/cuadro")
    public ResponseEntity<Map<String, Object>> publicarCuadro(@RequestBody CuadroEPPRequestDTO cuadro) {
        if (cuadro == null || cuadro.getPersonas() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "El cuadro no contiene 'personas'"));
        }
        cuadroEppService.publicar(cuadro);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("aceptado", true, "personas", cuadro.getPersonas().size()));
    }

    @GetMapping("/cuadro")
    public ResponseEntity<Map<String, Object>> obtenerCuadro() {
        return ResponseEntity.ok(cuadroEppService.obtenerVigente());
    }

    @GetMapping(value = "/cuadro/stream", produces = "text/event-stream")
    public SseEmitter suscribirseAlCuadro() {
        return sseNotificationService.registrarCliente();
    }

    @PostMapping("/frame")
    public ResponseEntity<Map<String, Object>> recibirFrame(@RequestBody byte[] jpeg) {
        visionMarcoService.guardar(jpeg);
        return ResponseEntity.accepted().body(Map.of("aceptado", true, "bytes", jpeg.length));
    }

    @GetMapping("/frame")
    public ResponseEntity<byte[]> obtenerFrame() {
        byte[] jpeg = visionMarcoService.obtener();
        if (jpeg == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noCache())
                .body(jpeg);
    }

    @GetMapping("/estado")
    public ResponseEntity<Map<String, Object>> estadoVision() {
        Map<String, Object> estado = new LinkedHashMap<>(visionMarcoService.estado());
        estado.put("framesStream", visionStreamService.framesRecibidos());
        estado.put("msDesdeUltimoFrameStream", visionStreamService.msDesdeUltimoFrame());
        return ResponseEntity.ok(estado);
    }

    @PostMapping("/stream")
    public ResponseEntity<Map<String, Object>> recibirStream(InputStream entrada)
            throws IOException {
        visionStreamService.recibir(entrada);
        return ResponseEntity.accepted()
                .body(Map.of("aceptado", true, "frames", visionStreamService.framesRecibidos()));
    }

    @GetMapping(value = "/stream.mjpg", produces = "multipart/x-mixed-replace;boundary=visionframe")
    public ResponseEntity<StreamingResponseBody> streamVideo() {
        StreamingResponseBody cuerpo = salida -> visionStreamService.escribirMultipart(salida, 5_000);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store, no-cache, must-revalidate")
                .header("Pragma", "no-cache")
                .contentType(MediaType.parseMediaType("multipart/x-mixed-replace;boundary=visionframe"))
                .body(cuerpo);
    }
}
