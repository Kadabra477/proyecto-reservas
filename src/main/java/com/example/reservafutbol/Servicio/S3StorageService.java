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
import net.coobird.thumbnailator.geometry.Positions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

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
     * Sube una imagen individual a S3 y devuelve su URL.
     * Genera una versión "original" y una "thumbnail".
     *
     * @param multipartFile El archivo de imagen a subir.
     * @param keyPrefix Un prefijo para la clave de S3 (ej. "cover/", "carousel/").
     * @return Un mapa con las URLs de la imagen original y la miniatura.
     * @throws IOException Si ocurre un error durante el procesamiento o subida.
     * @throws IllegalArgumentException Si el archivo no es una imagen válida o está vacío.
     */
    public Map<String, String> uploadSingleImageWithResolutions(MultipartFile multipartFile, String keyPrefix) throws IOException {
        if (multipartFile == null || multipartFile.isEmpty() || multipartFile.getContentType() == null
                || !multipartFile.getContentType().startsWith("image/")) {
            throw new IllegalArgumentException("El archivo debe ser una imagen válida (JPG, PNG, GIF, etc.) y no puede estar vacío.");
        }

        Map<String, String> imageUrls = new HashMap<>();
        String fileExtension = getFileExtension(multipartFile);

        BufferedImage originalImage = ImageIO.read(multipartFile.getInputStream());
        String baseFileName = UUID.randomUUID().toString() + "_" + System.currentTimeMillis();

        // --- Subir la versión ORIGINAL (para detalles/banners) ---
        String originalKey = complexFolder + keyPrefix + "original/" + baseFileName + "." + fileExtension;
        ByteArrayOutputStream originalOs = new ByteArrayOutputStream();
        try {
            Thumbnails.of(originalImage)
                    .scale(1.0) // Mantener tamaño original (o se podría redimensionar a un máximo si es muy grande)
                    .outputFormat("jpg") // Convertir a JPG para consistencia y compresión
                    .outputQuality(0.9) // Alta calidad
                    .toOutputStream(originalOs);

            InputStream originalIs = new ByteArrayInputStream(originalOs.toByteArray());
            ObjectMetadata originalMetadata = new ObjectMetadata();
            originalMetadata.setContentLength(originalOs.size());
            originalMetadata.setContentType("image/jpeg");
            s3Client.putObject(new PutObjectRequest(bucketName, originalKey, originalIs, originalMetadata));
            imageUrls.put("original", s3BaseUrl + originalKey);
            originalIs.close();
        } catch (IOException e) {
            log.error("Error al procesar/subir imagen original: {}", e.getMessage(), e);
            throw new IOException("Error al procesar la imagen original: " + e.getMessage(), e);
        } finally {
            originalOs.close();
        }

        // --- Generar y subir la versión THUMBNAIL (para tarjetas/miniaturas) ---
        String thumbnailKey = complexFolder + keyPrefix + "thumbnail/" + baseFileName + "." + fileExtension;
        ByteArrayOutputStream thumbnailOs = new ByteArrayOutputStream();
        try {
            Thumbnails.of(originalImage)
                    .size(400, 300) // Tamaño para miniaturas
                    .keepAspectRatio(true)
                    .outputFormat("jpg")
                    .outputQuality(0.7) // Calidad media para miniaturas
                    .toOutputStream(thumbnailOs);

            InputStream thumbnailIs = new ByteArrayInputStream(thumbnailOs.toByteArray());
            ObjectMetadata thumbnailMetadata = new ObjectMetadata();
            thumbnailMetadata.setContentLength(thumbnailOs.size());
            thumbnailMetadata.setContentType("image/jpeg");
            s3Client.putObject(new PutObjectRequest(bucketName, thumbnailKey, thumbnailIs, thumbnailMetadata));
            imageUrls.put("thumbnail", s3BaseUrl + thumbnailKey);
            thumbnailIs.close();
        } catch (IOException e) {
            log.error("Error al procesar/subir imagen thumbnail: {}", e.getMessage(), e);
            throw new IOException("Error al procesar la imagen thumbnail: " + e.getMessage(), e);
        } finally {
            thumbnailOs.close();
        }

        return imageUrls;
    }

    /**
     * Sube múltiples archivos de imagen para el carrusel.
     *
     * @param multipartFiles Array de archivos de imagen.
     * @return Lista de mapas, donde cada mapa contiene las URLs de las resoluciones para una imagen.
     * @throws IOException Si ocurre un error durante el procesamiento o subida.
     * @throws IllegalArgumentException Si algún archivo no es una imagen válida o está vacío.
     */
    public List<Map<String, String>> uploadCarouselImagesWithResolutions(MultipartFile[] multipartFiles) throws IOException {
        List<Map<String, String>> uploadedImages = new ArrayList<>();
        if (multipartFiles == null || multipartFiles.length == 0) {
            return uploadedImages;
        }

        for (MultipartFile file : multipartFiles) {
            if (file != null && !file.isEmpty()) {
                uploadedImages.add(uploadSingleImageWithResolutions(file, "carousel/"));
            }
        }
        return uploadedImages;
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

    private String getFileExtension(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.lastIndexOf('.') > 0) {
            return originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        }
        // Fallback si no se encuentra extensión en el nombre original
        if (file.getContentType().equals("image/jpeg")) return "jpg";
        if (file.getContentType().equals("image/png")) return "png";
        if (file.getContentType().equals("image/gif")) return "gif";
        return "jpg"; // Fallback general
    }
}
