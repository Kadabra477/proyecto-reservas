package com.example.reservafutbol.Configuracion; // O el paquete donde tengas tus configuraciones

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AwsS3Config {

    // Inyecta las variables de entorno que configuraste en Render
    @Value("${AWS_ACCESS_KEY_ID}")
    private String awsAccessKeyId;

    @Value("${AWS_SECRET_ACCESS_KEY}")
    private String awsSecretAccessKey;

    @Value("${AWS_REGION_STATIC}") // El nombre de la variable de entorno que usaste
    private String awsRegion;

    @Bean // Esto le dice a Spring que el método devuelve un bean que debe ser manejado por el contenedor de Spring
    public AmazonS3 amazonS3Client() {
        // Crea las credenciales de AWS usando tus variables de entorno
        AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(
                new BasicAWSCredentials(awsAccessKeyId, awsSecretAccessKey)
        );

        // Construye el cliente de S3
        return AmazonS3ClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion(Regions.fromName(awsRegion)) // Define la región de tu bucket
                .build();
    }
}