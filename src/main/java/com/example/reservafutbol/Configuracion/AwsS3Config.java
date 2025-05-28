package com.example.reservafutbol.Configuracion;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions; // Importar Regions
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger; // Importar Logger
import org.slf4j.LoggerFactory; // Importar LoggerFactory

@Configuration
public class AwsS3Config {

    private static final Logger log = LoggerFactory.getLogger(AwsS3Config.class); // Inicializar Logger

    @Value("${AWS_ACCESS_KEY_ID}")
    private String awsAccessKeyId;

    @Value("${AWS_SECRET_ACCESS_KEY}")
    private String awsSecretAccessKey;

    @Value("${AWS_REGION_STATIC}") // Se mantiene como String para inyección de valor
    private String awsRegionString; // Cambiado el nombre para evitar confusión

    @Bean
    public AmazonS3 amazonS3Client() {
        log.info("Initializing AmazonS3Client with region: {}", awsRegionString); // Log para depuración
        AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(
                new BasicAWSCredentials(awsAccessKeyId, awsSecretAccessKey)
        );

        // Convertir el string de la región a un enum de Regions
        Regions regionEnum;
        try {
            regionEnum = Regions.fromName(awsRegionString);
        } catch (IllegalArgumentException e) {
            log.error("Invalid AWS region provided: {}. Using default US_EAST_1.", awsRegionString, e); // Log de error si la región es inválida
            regionEnum = Regions.US_EAST_1; // Fallback a una región por defecto
        }

        return AmazonS3ClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion(regionEnum) // Usar el enum de la región
                .build();
    }
}