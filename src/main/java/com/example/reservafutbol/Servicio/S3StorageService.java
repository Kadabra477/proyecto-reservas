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

// Importaciones de Thumbnailator
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions; // No es estrictamente necesario si solo redimensionas, pero puede ser útil

import javax.imageio.ImageIO; // Para leer la imagen en BufferedImage
import java.awt.image.BufferedImage; // Para manejar la imagen en memoria
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File; // Se mantiene para el método original de uploadFile
import java.io.FileOutputStream; // Se mantiene para el método original de uploadFile
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

    // Carpeta para archivos de PERFILES (manteniendo tu lógica existente)
    private final String profileFolder = "perfiles/";

    // Carpeta para archivos de COMPLEJOS (nueva lógica)
    private final String complexFolder = "complejos/";

    /**
     * Sube un archivo MultipartFile a AWS S3 y devuelve su URL pública.
     * Este método se mantiene para la lógica de "perfiles/" y no incluye procesamiento de imagen.
     *
     * @param multipartFile El archivo a subir (ej. una imagen de perfil).
     * @return La URL pública del archivo subido en S3.
     * @throws IOException Si ocurre un error durante la conversión o subida.
     * @throws IllegalArgumentException Si el archivo no es válido o está vacío.
     */
    public String uploadFile(MultipartFile multipartFile) throws IOException {
        if (multipartFile.isEmpty()) {
            throw new IllegalArgumentException("El archivo no puede estar vacío.");
        }
        // Puedes añadir validación de tipo de archivo si es solo para imágenes de perfil.

        File file = convertMultiPartToFile(multipartFile);
        String fileName = profileFolder + System.currentTimeMillis() + "_" +
                Objects.requireNonNull(multipartFile.getOriginalFilename()).replace(" ", "_");

        try {
            s3Client.putObject(new PutObjectRequest(bucketName, fileName, file));
            String fileUrl = s3Client.getUrl(bucketName, fileName).toString();
            log.info("Archivo (perfil) subido exitosamente a S3: {}", fileUrl);
            return fileUrl;

        } catch (AmazonServiceException e) {
            log.error("Error en el servicio S3 al subir el archivo (perfil): {}", e.getMessage(), e);
            throw new RuntimeException("Error al subir el archivo a S3: " + e.getMessage());

        } catch (SdkClientException e) {
            log.error("Error de comunicación con AWS S3 al subir el archivo (perfil): {}", e.getMessage(), e);
            throw new RuntimeException("Error de red al subir el archivo a S3: " + e.getMessage());

        } finally {
            if (file.exists() && !file.delete()) {
                log.warn("No se pudo eliminar el archivo temporal (perfil): {}", file.getAbsolutePath());
            }
        }
    }

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
            // Intentar inferir la extensión del tipo de contenido si el nombre no la tiene
            if (multipartFile.getContentType().equals("image/jpeg")) fileExtension = "jpg";
            else if (multipartFile.getContentType().equals("image/png")) fileExtension = "png";
            else if (multipartFile.getContentType().equals("image/gif")) fileExtension = "gif";
            else fileExtension = "jpg"; // Fallback
        }

        // Generar un nombre de archivo único para S3, en la carpeta de complejos
        String key = complexFolder + UUID.randomUUID().toString() + "_" + System.currentTimeMillis() + "." + fileExtension;

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        InputStream is = null;

        try {
            BufferedImage originalImage = ImageIO.read(multipartFile.getInputStream());

            // **PROCESAMIENTO DE IMAGEN CON THUMBNAILATOR**
            // Redimensionar y Comprimir
            Thumbnails.of(originalImage)
                    .width(1200) // Ancho máximo de la imagen (ej. para detalles)
                    .height(800) // Alto máximo de la imagen
                    .keepAspectRatio(true) // Mantiene la relación de aspecto, no la distorsiona
                    .outputFormat("jpg") // Fuerza a JPG para compresión eficiente
                    .outputQuality(0.8) // Calidad de compresión (0.0 a 1.0, 0.8 es un buen equilibrio)
                    .toOutputStream(os);

            is = new ByteArrayInputStream(os.toByteArray()); // Convertir a InputStream para S3

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(os.size());
            metadata.setContentType("image/jpeg"); // Asegurarse de que el Content-Type coincida con el outputFormat

            // Subir la imagen procesada a S3
            s3Client.putObject(new PutObjectRequest(bucketName, key, is, metadata));

            // Devolver la URL pública del objeto subido
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
     * Se mantiene para el método original de uploadFile (perfiles).
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

    // Método para eliminar archivos de S3 (puede usarse para ambos tipos si la URL lo permite)
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank() || !fileUrl.startsWith(s3BaseUrl)) {
            log.warn("URL de archivo S3 inválida o no gestionada por este servicio: {}", fileUrl);
            return;
        }
        // Extraer la "key" (nombre del archivo en S3) de la URL
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