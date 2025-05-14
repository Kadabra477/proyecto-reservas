package com.example.reservafutbol.Servicio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.io.IOException;

@Service
public class PagoServicio {

    private static final Logger log = LoggerFactory.getLogger(PagoServicio.class);  // Añadir Logger
    private static final String ACCESS_TOKEN = "TEST-848f4219-626f-4602-bbff-4c812421070f";
    private static final String BASE_URL = "https://api.mercadopago.com/v1/";

    public JsonNode obtenerPagoPorId(String paymentId) throws Exception {
        OkHttpClient client = new OkHttpClient();
        String url = BASE_URL + "payments/" + paymentId + "?access_token=" + ACCESS_TOKEN;

        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("Error al consultar pago: {} - {}", response.code(), response.message());
                throw new RuntimeException("Error al consultar pago: " + response.message());
            }

            ObjectMapper mapper = new ObjectMapper();
            return mapper.readTree(response.body().string());
        } catch (IOException e) {
            log.error("Error al procesar la respuesta de Mercado Pago: {}", e.getMessage(), e);
            throw new RuntimeException("Error de conexión con Mercado Pago", e);  // Asegúrate de pasar la excepción original
        }
    }
}
