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

/**
 * Canal de visión por computer vision: publica el cuadro de cumplimiento de EPP
 * en vivo y lo retransmite a la vista de cámara.
 *
 * Separado de `/api/v1/seguridad` a propósito. Ahí viven los hechos que se
 * archivan (infracciones, anomalías). Esto es el estado presente de la escena: se
 * sobrescribe constantemente y se descarta al dejar de recibir señal, porque
 * persistirlo no aporta nada consultable.
 */
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

    /**
     * Recibe el cuadro del motor de visión y lo reemite por SSE.
     * Responde 202: se aceptó y ya se emitió, no se almacenó.
     */
    @PostMapping("/cuadro")
    public ResponseEntity<Map<String, Object>> publicarCuadro(@RequestBody CuadroEPPRequestDTO cuadro) {
        if (cuadro == null || cuadro.getPersonas() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "El cuadro no contiene 'personas'"));
        }
        cuadroEppService.publicar(cuadro);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("aceptado", true, "personas", cuadro.getPersonas().size()));
    }

    /**
     * Último cuadro vigente. La vista lo consulta al abrirse para no mostrar un
     * panel vacío mientras espera el primer evento SSE.
     */
    @GetMapping("/cuadro")
    public ResponseEntity<Map<String, Object>> obtenerCuadro() {
        return ResponseEntity.ok(cuadroEppService.obtenerVigente());
    }

    /**
     * Suscripción SSE exclusiva al cuadro, para quien no quiera todo el tráfico
     * de alertas del stream general.
     */
    @GetMapping(value = "/cuadro/stream", produces = "text/event-stream")
    public SseEmitter suscribirseAlCuadro() {
        return sseNotificationService.registrarCliente();
    }

    /**
     * Recibe el frame ya anotado por el motor y lo guarda como imagen vigente.
     * Lo publica el cliente Python, no el navegador: la webcam sólo puede estar
     * en un proceso a la vez y el motor es su propietario.
     */
    @PostMapping("/frame")
    public ResponseEntity<Map<String, Object>> recibirFrame(@RequestBody byte[] jpeg) {
        visionMarcoService.guardar(jpeg);
        return ResponseEntity.accepted().body(Map.of("aceptado", true, "bytes", jpeg.length));
    }

    /**
     * Devuelve el último frame anotado. Responde 204 mientras el motor no
     * haya publicado ninguno vigente, para que la vista muestre "sin señal"
     * en vez de una imagen congelada de hace un minuto.
     */
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

    /**
     * Ingesta del video: el motor mantiene esta conexión abierta y va
     * escribiendo frames JPEG con prefijo de longitud de 4 bytes.
     *
     * No devuelve cuerpo. La respuesta sólo confirma que el flujo se aceptó;
     * el video viaja de vuelta por el stream MJPEG, no por aquí.
     */
    @PostMapping("/stream")
    public ResponseEntity<Map<String, Object>> recibirStream(@RequestBody InputStream entrada)
            throws IOException {
        visionStreamService.recibir(entrada);
        return ResponseEntity.accepted()
                .body(Map.of("aceptado", true, "frames", visionStreamService.framesRecibidos()));
    }

    /**
     * Reproducción MJPEG. Un {@code <img src=...>} contra esta ruta muestra
     * video continuo: no es un video con marca de tiempo sino una sucesión de
     * JPEG que el navegador va pintando según llegan.
     */
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
