package com.example.reservafutbol.Servicio;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class S3StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final AmazonS3 s3Client;
    private final String bucketName;
    private final String complexFolder = "complejos/";

    public S3StorageService(@Value("${aws.s3.bucket-name}") String bucketName,
                            @Value("${AWS_ACCESS_KEY_ID}") String awsAccessKeyId,
                            @Value("${AWS_SECRET_ACCESS_KEY}") String awsSecretAccessKey,
                            @Value("${AWS_REGION_STATIC}") String awsRegionStatic) {

        this.bucketName = bucketName;

        AWSStaticCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(
                new BasicAWSCredentials(awsAccessKeyId, awsSecretAccessKey)
        );

        Regions regionEnum;
        try {
            regionEnum = Regions.fromName(awsRegionStatic);
        } catch (IllegalArgumentException e) {
            log.error("Invalid AWS region provided: {}. Using default US_EAST_1.", awsRegionStatic, e);
            regionEnum = Regions.US_EAST_1;
        }

        this.s3Client = AmazonS3ClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion(regionEnum)
                .build();
    }

    /**
     * Sube una imagen individual a S3 y devuelve las URLs de la versión "original" y "thumbnail".
     *
     * @param multipartFile El archivo de imagen a subir.
     * @param keyPrefix Un prefijo para la clave de S3 (ej. "cover/", "carousel/").
     * @return Un mapa con las URLs de la imagen original y la miniatura.
     * @throws IOException Si ocurre un error durante el procesamiento o subida.
     * @throws IllegalArgumentException Si el archivo no es una imagen válida o está vacío.
     */
    public Map<String, String> uploadImageWithResolutions(MultipartFile multipartFile, String keyPrefix) throws IOException {
        if (multipartFile == null || multipartFile.isEmpty() || multipartFile.getContentType() == null
                || !multipartFile.getContentType().startsWith("image/")) {
            throw new IllegalArgumentException("El archivo debe ser una imagen válida y no puede estar vacío.");
        }

        Map<String, String> imageUrls = new HashMap<>();
        String fileExtension = getFileExtension(multipartFile);
        String baseFileName = UUID.randomUUID().toString() + "_" + System.currentTimeMillis();

        try (InputStream originalInputStream = multipartFile.getInputStream();
             ByteArrayOutputStream originalOs = new ByteArrayOutputStream()) {

            BufferedImage originalImage = ImageIO.read(originalInputStream);

            // Subir la versión ORIGINAL
            Thumbnails.of(originalImage)
                    .scale(1.0)
                    .outputFormat("jpg")
                    .outputQuality(0.9)
                    .toOutputStream(originalOs);

            try (InputStream originalIs = new ByteArrayInputStream(originalOs.toByteArray())) {
                String originalKey = complexFolder + keyPrefix + "original/" + baseFileName + ".jpg";
                ObjectMetadata originalMetadata = new ObjectMetadata();
                originalMetadata.setContentLength(originalOs.size());
                originalMetadata.setContentType("image/jpeg");
                s3Client.putObject(new PutObjectRequest(bucketName, originalKey, originalIs, originalMetadata));

                // Genera la URL de forma segura usando el cliente de S3
                imageUrls.put("original", s3Client.getUrl(bucketName, originalKey).toString());
            }

            // Generar y subir la versión THUMBNAIL
            ByteArrayOutputStream thumbnailOs = new ByteArrayOutputStream();
            Thumbnails.of(originalImage)
                    .size(400, 300)
                    .keepAspectRatio(true)
                    .outputFormat("jpg")
                    .outputQuality(0.7)
                    .toOutputStream(thumbnailOs);

            try (InputStream thumbnailIs = new ByteArrayInputStream(thumbnailOs.toByteArray())) {
                String thumbnailKey = complexFolder + keyPrefix + "thumbnail/" + baseFileName + ".jpg";
                ObjectMetadata thumbnailMetadata = new ObjectMetadata();
                thumbnailMetadata.setContentLength(thumbnailOs.size());
                thumbnailMetadata.setContentType("image/jpeg");
                s3Client.putObject(new PutObjectRequest(bucketName, thumbnailKey, thumbnailIs, thumbnailMetadata));

                // Genera la URL de forma segura usando el cliente de S3
                imageUrls.put("thumbnail", s3Client.getUrl(bucketName, thumbnailKey).toString());
            }
        } catch (IOException e) {
            log.error("Error al procesar/subir la imagen: {}", e.getMessage(), e);
            throw new IOException("Error al procesar la imagen: " + e.getMessage(), e);
        }

        return imageUrls;
    }

    /**
     * Sube múltiples archivos de imagen y devuelve las URLs de la versión "original" para cada uno.
     * Se redimensiona y comprime cada imagen para optimizar el rendimiento del carrusel.
     *
     * @param multipartFiles Array de archivos de imagen.
     * @param keyPrefix Un prefijo para la clave de S3 (ej. "carousel/").
     * @return Lista de mapas, donde cada mapa contiene la URL de la versión original.
     * @throws IOException Si ocurre un error durante el procesamiento o subida.
     * @throws IllegalArgumentException Si algún archivo no es una imagen válida o está vacío.
     */
    public List<Map<String, String>> uploadMultipleImagesWithResolutions(MultipartFile[] multipartFiles, String keyPrefix) throws IOException {
        List<Map<String, String>> uploadedImages = new ArrayList<>();
        if (multipartFiles == null || multipartFiles.length == 0) {
            return uploadedImages;
        }

        for (MultipartFile file : multipartFiles) {
            if (file != null && !file.isEmpty()) {
                String baseFileName = UUID.randomUUID().toString() + "_" + System.currentTimeMillis();
                String originalKey = complexFolder + keyPrefix + baseFileName + ".jpg";

                try (InputStream originalInputStream = file.getInputStream();
                     ByteArrayOutputStream processedOs = new ByteArrayOutputStream()) {

                    BufferedImage originalImage = ImageIO.read(originalInputStream);

                    // Redimensionar y comprimir la imagen para el carrusel
                    Thumbnails.of(originalImage)
                            .width(1920)
                            .outputFormat("jpg")
                            .outputQuality(0.8)
                            .toOutputStream(processedOs);

                    try (InputStream processedIs = new ByteArrayInputStream(processedOs.toByteArray())) {
                        ObjectMetadata metadata = new ObjectMetadata();
                        metadata.setContentLength(processedOs.size());
                        metadata.setContentType("image/jpeg");
                        s3Client.putObject(new PutObjectRequest(bucketName, originalKey, processedIs, metadata));

                        uploadedImages.add(Map.of("original", s3Client.getUrl(bucketName, originalKey).toString()));
                    }
                }
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
        if (fileUrl == null || fileUrl.isBlank() || !fileUrl.contains(bucketName + ".")) {
            log.warn("URL de archivo S3 inválida o no gestionada por este servicio: {}", fileUrl);
            return;
        }
        try {
            String urlPath = new java.net.URL(fileUrl).getPath();
            String key = urlPath.substring(1); // Elimina la barra inicial
            s3Client.deleteObject(bucketName, key);
            log.info("Archivo eliminado de S3: {}", fileUrl);
        } catch (AmazonServiceException e) {
            log.error("Error en el servicio S3 al eliminar el archivo: {}", e.getMessage(), e);
            throw new RuntimeException("Error al eliminar el archivo de S3: " + e.getMessage());
        } catch (SdkClientException e) {
            log.error("Error de comunicación con AWS S3 al eliminar el archivo: {}", e.getMessage(), e);
            throw new RuntimeException("Error de red al eliminar el archivo de S3: " + e.getMessage());
        } catch (java.net.MalformedURLException e) {
            log.error("URL de archivo S3 malformada: {}", fileUrl, e);
        }
    }

    private String getFileExtension(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.lastIndexOf('.') > 0) {
            return originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        }
        return "jpg";
    }
}