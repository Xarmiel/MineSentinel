package com.sentinelmine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

/**
 * Servicio para la gestión y almacenamiento físico de evidencias visuales (Snapshots)
 * generadas por YOLOv8 o capturadas por el frontend.
 */
@Service
public class SnapshotStorageService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotStorageService.class);

    private final Path rootLocation;

    public SnapshotStorageService(@Value("${minesentinel.snapshots.dir:./uploads/snapshots}") String storageDir) {
        this.rootLocation = Paths.get(storageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootLocation);
            log.info("Directorio de almacenamiento de evidencias visuales inicializado en: {}", this.rootLocation);
        } catch (IOException e) {
            log.error("No se pudo crear el directorio de snapshots: {}", e.getMessage());
        }
    }

    /**
     * Guarda un archivo Multipart de imagen y devuelve la URL accesible.
     */
    public String guardarSnapshot(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo de imagen no puede estar vacío");
        }

        String extension = ".jpg";
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uniqueName = "snapshot_" + timestamp + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;

        Path targetLocation = this.rootLocation.resolve(uniqueName);
        Files.copy(file.getInputStream(), targetLocation);

        log.info("Evidencia visual guardada: {}", uniqueName);
        return "/api/v1/evidencias/" + uniqueName;
    }

    /**
     * Guarda una imagen en formato Base64 (útil para streams de OpenCV / Webcams).
     */
    public String guardarSnapshotBase64(String base64Data, String extension) throws IOException {
        if (base64Data == null || base64Data.isBlank()) {
            throw new IllegalArgumentException("La cadena base64 no puede estar vacía");
        }

        String cleanBase64 = base64Data;
        if (cleanBase64.contains(",")) {
            cleanBase64 = cleanBase64.substring(cleanBase64.indexOf(",") + 1);
        }

        byte[] decodedBytes = Base64.getDecoder().decode(cleanBase64);

        String ext = (extension != null && !extension.isBlank()) ? (extension.startsWith(".") ? extension : "." + extension) : ".jpg";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uniqueName = "snapshot_" + timestamp + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;

        Path targetLocation = this.rootLocation.resolve(uniqueName);
        try (FileOutputStream fos = new FileOutputStream(targetLocation.toFile())) {
            fos.write(decodedBytes);
        }

        log.info("Evidencia visual Base64 guardada: {}", uniqueName);
        return "/api/v1/evidencias/" + uniqueName;
    }

    /**
     * Carga el archivo como recurso Spring para su descarga o visualización en el navegador.
     */
    public Resource cargarComoRecurso(String filename) {
        try {
            Path filePath = this.rootLocation.resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new IllegalArgumentException("No se encontró el archivo de evidencia: " + filename);
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Ruta de archivo inválida: " + filename, e);
        }
    }
}
