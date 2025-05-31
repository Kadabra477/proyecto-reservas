package com.example.reservafutbol.Servicio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value; // Importar Value
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class PagoServicio {

    private static final Logger log = LoggerFactory.getLogger(PagoServicio.class);

    @Value("${MERCADO_PAGO_ACCESS_TOKEN}") // Inyectar el token desde properties
    private String accessToken;

    private static final String BASE_URL = "https://api.mercadopago.com/v1/";

    public JsonNode obtenerPagoPorId(String paymentId) throws Exception {
        OkHttpClient client = new OkHttpClient();
        String url = BASE_URL + "payments/" + paymentId + "?access_token=" + accessToken; // Usar el token inyectado

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer " + accessToken) // Mejor práctica: enviar token en el header Authorization
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "No response body";
                log.error("Error al consultar pago a Mercado Pago: Código={}, Mensaje={}, Body={}",
                        response.code(), response.message(), responseBody);
                throw new RuntimeException("Error al consultar pago: " + response.message() + " - " + responseBody);
            }

            ObjectMapper mapper = new ObjectMapper();
            String responseBody = response.body().string();
            // Asegúrate de que el body no esté vacío antes de leerlo como JSON
            if (responseBody == null || responseBody.trim().isEmpty()) {
                log.warn("Respuesta vacía al consultar pago {} de Mercado Pago.", paymentId);
                return mapper.readTree("{}"); // Devuelve un JSON vacío para evitar NPE
            }
            return mapper.readTree(responseBody);
        } catch (IOException e) {
            log.error("Error de I/O o al procesar la respuesta JSON de Mercado Pago para pago {}: {}", paymentId, e.getMessage(), e);
            throw new RuntimeException("Error de conexión/parsing con Mercado Pago", e);
        }
    }
}