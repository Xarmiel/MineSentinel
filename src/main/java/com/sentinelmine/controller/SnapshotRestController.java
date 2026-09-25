package com.sentinelmine.controller;

import com.sentinelmine.service.SnapshotStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Controlador REST para subir y descargar snapshots de evidencias visuales.
 */
@RestController
@RequestMapping("/api/v1/evidencias")
@CrossOrigin(origins = "*")
public class SnapshotRestController {

    private final SnapshotStorageService snapshotStorageService;

    public SnapshotRestController(SnapshotStorageService snapshotStorageService) {
        this.snapshotStorageService = snapshotStorageService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> subirSnapshot(@RequestParam("file") MultipartFile file) {
        try {
            String url = snapshotStorageService.guardarSnapshot(file);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("snapshotUrl", url));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al guardar evidencia: " + e.getMessage()));
        }
    }

    @PostMapping("/upload-base64")
    public ResponseEntity<Map<String, String>> subirSnapshotBase64(@RequestBody Map<String, String> payload) {
        try {
            String base64 = payload.get("base64");
            String extension = payload.getOrDefault("extension", ".jpg");
            String url = snapshotStorageService.guardarSnapshotBase64(base64, extension);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("snapshotUrl", url));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al procesar base64: " + e.getMessage()));
        }
    }

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> obtenerSnapshot(@PathVariable String filename) {
        try {
            Resource file = snapshotStorageService.cargarComoRecurso(filename);
            String contentType = "image/jpeg";
            if (filename.toLowerCase().endsWith(".png")) {
                contentType = "image/png";
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .body(file);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
