package com.sentinelmine.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotStorageServiceTest {

    @Test
    @DisplayName("Debe guardar un archivo Multipart y retornar una URL accesible")
    void testGuardarSnapshotMultipart(@TempDir Path tempDir) throws IOException {
        SnapshotStorageService storageService = new SnapshotStorageService(tempDir.toString());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "evidencia_epp.jpg",
                "image/jpeg",
                "datos_imagen_dummy".getBytes()
        );

        String url = storageService.guardarSnapshot(file);

        assertNotNull(url);
        assertTrue(url.startsWith("/api/v1/evidencias/snapshot_"));
        assertTrue(url.endsWith(".jpg"));

        String filename = url.replace("/api/v1/evidencias/", "");
        Resource resource = storageService.cargarComoRecurso(filename);
        assertTrue(resource.exists());
    }

    @Test
    @DisplayName("Debe guardar imagen en Base64")
    void testGuardarSnapshotBase64(@TempDir Path tempDir) throws IOException {
        SnapshotStorageService storageService = new SnapshotStorageService(tempDir.toString());

        String base64Data = "data:image/jpeg;base64,AQIDBAU=";
        String url = storageService.guardarSnapshotBase64(base64Data, "jpg");

        assertNotNull(url);
        assertTrue(url.startsWith("/api/v1/evidencias/snapshot_"));
    }

    @Test
    @DisplayName("Debe lanzar excepción si el archivo está vacío")
    void testGuardarArchivoVacioLanzaExcepcion(@TempDir Path tempDir) {
        SnapshotStorageService storageService = new SnapshotStorageService(tempDir.toString());
        MockMultipartFile emptyFile = new MockMultipartFile("file", "", "image/jpeg", new byte[0]);

        assertThrows(IllegalArgumentException.class, () -> storageService.guardarSnapshot(emptyFile));
    }
}
