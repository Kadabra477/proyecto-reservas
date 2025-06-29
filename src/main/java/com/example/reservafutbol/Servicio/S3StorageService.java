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
import java.io.File; // Se mantiene si convertMultiPartToFile se usara para algo más. Si no, se puede quitar.
import java.io.FileOutputStream; // Se mantiene si convertMultiPartToFile se usara para algo más.
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
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

    // ¡Carpeta para archivos de PERFILES ELIMINADA si ya no se usa!

    // Carpeta para archivos de COMPLEJOS (nueva lógica)
    private final String complexFolder = "complejos/";

    // ¡Método 'uploadFile' (para perfiles) ELIMINADO!

    /**
     * Sube un archivo MultipartFile de imagen a AWS S3 después de procesarlo (redimensionar y comprimir),
     * y devuelve su URL pública. Este es el método para imágenes de complejos.
     *
     * @param multipartFile El archivo de imagen a subir (ej. una foto de complejo).
     * @return La URL pública del archivo subido en S3.
     * @throws IOException Si ocurre un error durante el procesamiento o subida.
     * @throws IllegalArgumentException Si el archivo no es una imagen válida o está vacío.
     */
    public String uploadComplexImage(MultipartFile multipartFile) throws IOException {
        if (multipartFile.isEmpty() || multipartFile.getContentType() == null
                || !multipartFile.getContentType().startsWith("image/")) {
            throw new IllegalArgumentException("El archivo debe ser una imagen válida (JPG, PNG, GIF, etc.) y no puede estar vacío.");
        }

        String originalFilename = multipartFile.getOriginalFilename();
        String fileExtension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            fileExtension = originalFilename.substring(dotIndex + 1).toLowerCase();
        } else {
            if (multipartFile.getContentType().equals("image/jpeg")) fileExtension = "jpg";
            else if (multipartFile.getContentType().equals("image/png")) fileExtension = "png";
            else if (multipartFile.getContentType().equals("image/gif")) fileExtension = "gif";
            else fileExtension = "jpg"; // Fallback
        }

        String key = complexFolder + UUID.randomUUID().toString() + "_" + System.currentTimeMillis() + "." + fileExtension;

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        InputStream is = null;

        try {
            BufferedImage originalImage = ImageIO.read(multipartFile.getInputStream());

            Thumbnails.of(originalImage)
                    .width(1200)
                    .height(800)
                    .keepAspectRatio(true)
                    .outputFormat("jpg")
                    .outputQuality(0.8)
                    .toOutputStream(os);

            is = new ByteArrayInputStream(os.toByteArray());

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(os.size());
            metadata.setContentType("image/jpeg");

            s3Client.putObject(new PutObjectRequest(bucketName, key, is, metadata));

            return s3BaseUrl + key;

        } catch (AmazonServiceException e) {
            log.error("Error en el servicio S3 al subir la imagen de complejo: {}", e.getMessage(), e);
            throw new RuntimeException("Error en el servicio de AWS S3 al procesar y subir imagen: " + e.getMessage());
        } catch (SdkClientException e) {
            log.error("Error de comunicación con AWS S3 al subir la imagen de complejo: {}", e.getMessage(), e);
            throw new RuntimeException("Error de red o cliente S3 al procesar y subir imagen: " + e.getMessage());
        } catch (IOException e) {
            log.error("Error de I/O al procesar la imagen de complejo: {}", e.getMessage(), e);
            throw new IOException("Error al leer o procesar la imagen: " + e.getMessage(), e);
        } finally {
            if (is != null) {
                try { is.close(); } catch (IOException e) { log.warn("Error al cerrar InputStream: {}", e.getMessage()); }
            }
            try { os.close(); } catch (IOException e) { log.warn("Error al cerrar ByteArrayOutputStream: {}", e.getMessage()); }
        }
    }

    /**
     * Convierte un MultipartFile en un archivo temporal.
     * Este método PUEDE SER ELIMINADO si ya no se usa en ningún otro lado.
     * Actualmente el único método que lo usaba (uploadFile) ha sido eliminado.
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

    /**
     * Método para eliminar archivos de S3. Ahora solo se usa para imágenes de complejo.
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
}