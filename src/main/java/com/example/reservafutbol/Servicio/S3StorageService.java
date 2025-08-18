package com.example.reservafutbol.Servicio;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions; // Importación necesaria para usar Positions

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap; // Importar HashMap
import java.util.Map;   // Importar Map
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final AmazonS3 s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.base-url}")
    private String s3BaseUrl;

    private final String complexFolder = "complejos/";

    /**
     * Sube un archivo MultipartFile de imagen a AWS S3 después de procesarlo (redimensionar y comprimir),
     * generando múltiples resoluciones, y devuelve un mapa con sus URL públicas.
     *
     * @param multipartFile El archivo de imagen a subir (ej. una foto de complejo).
     * @return Un mapa donde la clave es el tipo de resolución (ej. "thumbnail", "large") y el valor es la URL pública.
     * @throws IOException Sí ocurre un error durante el procesamiento o subida.
     * @throws IllegalArgumentException Si el archivo no es una imagen válida o está vacío.
     */
    public Map<String, String> uploadComplexImageWithResolutions(MultipartFile multipartFile) throws IOException {
        if (multipartFile.isEmpty() || multipartFile.getContentType() == null
                || !multipartFile.getContentType().startsWith("image/")) {
            throw new IllegalArgumentException("El archivo debe ser una imagen válida (JPG, PNG, GIF, etc.) y no puede estar vacío.");
        }

        Map<String, String> imageUrls = new HashMap<>();
        String originalFilename = multipartFile.getOriginalFilename();
        String fileExtension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            fileExtension = originalFilename.substring(dotIndex + 1).toLowerCase();
        } else {
            // Fallback para extensión si no se encuentra en el nombre original
            if (multipartFile.getContentType().equals("image/jpeg")) fileExtension = "jpg";
            else if (multipartFile.getContentType().equals("image/png")) fileExtension = "png";
            else if (multipartFile.getContentType().equals("image/gif")) fileExtension = "gif";
            else fileExtension = "jpg"; // Fallback general
        }

        BufferedImage originalImage = ImageIO.read(multipartFile.getInputStream());

        // --- Generar y subir la versión GRANDE (para banners/detalles) ---
        String largeKey = complexFolder + "large/" + UUID.randomUUID().toString() + "_" + System.currentTimeMillis() + "." + fileExtension;
        ByteArrayOutputStream largeOs = new ByteArrayOutputStream();
        try {
            Thumbnails.of(originalImage)
                    .size(1920, 1080) // Redimensionar a un tamaño grande, manteniendo aspecto
                    .keepAspectRatio(true)
                    .outputFormat("jpg")
                    .outputQuality(0.85) // Mayor calidad para la imagen grande
                    .toOutputStream(largeOs);

            InputStream largeIs = new ByteArrayInputStream(largeOs.toByteArray());
            ObjectMetadata largeMetadata = new ObjectMetadata();
            largeMetadata.setContentLength(largeOs.size());
            largeMetadata.setContentType("image/jpeg");
            s3Client.putObject(new PutObjectRequest(bucketName, largeKey, largeIs, largeMetadata));
            imageUrls.put("large", s3BaseUrl + largeKey);
            largeIs.close(); // Cerrar InputStream después de usar
        } catch (IOException e) {
            log.error("Error al procesar/subir imagen grande: {}", e.getMessage(), e);
            throw new IOException("Error al procesar la imagen grande: " + e.getMessage(), e);
        } finally {
            largeOs.close();
        }

        // --- Generar y subir la versión PEQUEÑA (para tarjetas/miniaturas) ---
        String thumbnailKey = complexFolder + "thumbnail/" + UUID.randomUUID().toString() + "_" + System.currentTimeMillis() + "." + fileExtension;
        ByteArrayOutputStream thumbnailOs = new ByteArrayOutputStream();
        try {
            Thumbnails.of(originalImage)
                    .size(400, 300) // Redimensionar a un tamaño más pequeño
                    .keepAspectRatio(true)
                    .outputFormat("jpg")
                    .outputQuality(0.7) // Menor calidad para miniaturas, si es aceptable
                    .toOutputStream(thumbnailOs);

            InputStream thumbnailIs = new ByteArrayInputStream(thumbnailOs.toByteArray());
            ObjectMetadata thumbnailMetadata = new ObjectMetadata();
            thumbnailMetadata.setContentLength(thumbnailOs.size());
            thumbnailMetadata.setContentType("image/jpeg");
            s3Client.putObject(new PutObjectRequest(bucketName, thumbnailKey, thumbnailIs, thumbnailMetadata));
            imageUrls.put("thumbnail", s3BaseUrl + thumbnailKey);
            thumbnailIs.close(); // Cerrar InputStream después de usar
        } catch (IOException e) {
            log.error("Error al procesar/subir imagen thumbnail: {}", e.getMessage(), e);
            throw new IOException("Error al procesar la imagen thumbnail: " + e.getMessage(), e);
        } finally {
            thumbnailOs.close();
        }

        // Puedes añadir más resoluciones aquí si las necesitas (ej. "medium", "original")

        return imageUrls;
    }

    /**
     * Método para eliminar archivos de S3.
     *
     * @param fileUrl La URL del archivo a eliminar en S3.
     */
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank() || !fileUrl.startsWith(s3BaseUrl)) {
            log.warn("URL de archivo S3 inválida o no gestionada por este servicio: {}", fileUrl);
            return;
        }
        String key = fileUrl.substring(s3BaseUrl.length());
        try {
            s3Client.deleteObject(bucketName, key);
            log.info("Archivo eliminado de S3: {}", fileUrl);
        } catch (AmazonServiceException e) {
            log.error("Error en el servicio S3 al eliminar el archivo: {}", e.getMessage(), e);
            throw new RuntimeException("Error al eliminar el archivo de S3: " + e.getMessage());
        } catch (SdkClientException e) {
            log.error("Error de comunicación con AWS S3 al eliminar el archivo: {}", e.getMessage(), e);
            throw new RuntimeException("Error de red al eliminar el archivo de S3: " + e.getMessage());
        }
    }

    // Este método ya no se usa y puede ser eliminado si no hay otras dependencias.
    // private File convertMultiPartToFile(MultipartFile file) throws IOException { /* ... */ }
}
