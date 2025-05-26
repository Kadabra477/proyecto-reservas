package com.example.reservafutbol.Servicio;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.PutObjectRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class S3StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final AmazonS3 s3Client; // Inyecta el cliente S3 autoconfigurado por Spring

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    // Puedes definir una carpeta dentro de tu bucket para organizar las imágenes de perfil
    private final String folder = "perfiles/";

    /**
     * Sube un archivo MultipartFile a AWS S3 y devuelve su URL pública.
     * El archivo se convierte temporalmente a File para la subida.
     *
     * @param multipartFile El archivo de imagen a subir.
     * @return La URL pública del archivo subido en S3.
     * @throws IOException Si ocurre un error durante la conversión o subida del archivo.
     */
    public String uploadFile(MultipartFile multipartFile) throws IOException {
        // Convertir MultipartFile a File (AWS SDK lo requiere así para la subida)
        File file = convertMultiPartToFile(multipartFile);
        // Generar un nombre único para el archivo en S3
        String fileName = folder + System.currentTimeMillis() + "_" +
                Objects.requireNonNull(multipartFile.getOriginalFilename()).replace(" ", "_");
        String fileUrl = "";

        try {
            // Sube el archivo al bucket S3 con permisos de lectura pública
            s3Client.putObject(new PutObjectRequest(bucketName, fileName, file)
                    .withCannedAcl(CannedAccessControlList.PublicRead)); // ¡Esto lo hace público!
            fileUrl = s3Client.getUrl(bucketName, fileName).toString(); // Obtiene la URL pública del archivo
            log.info("Archivo subido a S3: {}", fileUrl);
        } finally {
            // Asegúrate de eliminar el archivo temporal creado localmente
            file.delete();
        }
        return fileUrl;
    }

    /**
     * Convierte un MultipartFile a un File temporal.
     * @param file El MultipartFile a convertir.
     * @return El File temporal.
     * @throws IOException Si ocurre un error de E/S.
     */
    private File convertMultiPartToFile(MultipartFile file) throws IOException {
        File convFile = new File(Objects.requireNonNull(file.getOriginalFilename()));
        FileOutputStream fos = new FileOutputStream(convFile);
        fos.write(file.getBytes());
        fos.close();
        return convFile;
    }
}