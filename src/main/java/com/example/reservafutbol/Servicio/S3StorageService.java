package com.example.reservafutbol.Servicio;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList; // Mantener este import si lo necesitas para otras cosas, pero ya no se usará directamente en putObject
import com.amazonaws.services.s3.model.PutObjectRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class S3StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final AmazonS3 s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    private final String folder = "perfiles/";

    /**
     * Sube un archivo MultipartFile a AWS S3 y devuelve su URL pública.
     *
     * @param multipartFile El archivo de imagen a subir.
     * @return La URL pública del archivo subido en S3.
     * @throws IOException Si ocurre un error durante la conversión o subida.
     */
    public String uploadFile(MultipartFile multipartFile) throws IOException {
        if (multipartFile.isEmpty() || multipartFile.getContentType() == null
                || !multipartFile.getContentType().startsWith("image/")) {
            throw new IllegalArgumentException("El archivo debe ser una imagen válida.");
        }

        File file = convertMultiPartToFile(multipartFile);
        String fileName = folder + System.currentTimeMillis() + "_" +
                Objects.requireNonNull(multipartFile.getOriginalFilename()).replace(" ", "_");

        try {
            // Eliminar la configuración de ACL, ya que el bucket no las permite.
            // La visibilidad pública se maneja ahora exclusivamente por la Política de Bucket.
            s3Client.putObject(new PutObjectRequest(bucketName, fileName, file)); // <-- ¡Cambio aquí! Se eliminó .withCannedAcl(...)

            String fileUrl = s3Client.getUrl(bucketName, fileName).toString();
            log.info("Archivo subido exitosamente a S3: {}", fileUrl);
            return fileUrl;

        } catch (AmazonServiceException e) {
            log.error("Error en el servicio S3: {}", e.getMessage(), e);
            throw new RuntimeException("Error al subir el archivo a S3: " + e.getMessage());

        } catch (SdkClientException e) {
            log.error("Error de comunicación con AWS S3: {}", e.getMessage(), e);
            throw new RuntimeException("Error de red al subir el archivo a S3: " + e.getMessage());

        } finally {
            if (file.exists() && !file.delete()) {
                log.warn("No se pudo eliminar el archivo temporal: {}", file.getAbsolutePath());
            }
        }
    }

    /**
     * Convierte un MultipartFile en un archivo temporal.
     *
     * @param file El MultipartFile recibido.
     * @return El archivo temporal.
     * @throws IOException Si falla la conversión.
     */
    private File convertMultiPartToFile(MultipartFile file) throws IOException {
        File tempFile = File.createTempFile("upload_", "_" + Objects.requireNonNull(file.getOriginalFilename()));
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(file.getBytes());
        }
        return tempFile;
    }
}