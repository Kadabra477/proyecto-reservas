package com.example.reservafutbol.Servicio;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.preference.Preference;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode; // Importar RoundingMode
import java.util.ArrayList;
import java.util.List;

@Service
public class MercadoPagoService {

    @Value("${backend.url}")
    private String backendUrl;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Value("${mercadopago.notification.url}")
    private String notificationUrl;

    @Value("${frontend.url.success}")
    private String frontendSuccessUrl;

    @Value("${frontend.url.failure}")
    private String frontendFailureUrl;

    @Value("${frontend.url.pending}")
    private String frontendPendingUrl;


    private static final Logger log = LoggerFactory.getLogger(MercadoPagoService.class);

    @Value("${MERCADO_PAGO_ACCESS_TOKEN}")
    private String accessToken;

    @PostConstruct
    public void init() {
        log.info("Configurando Mercado Pago SDK...");
        if (accessToken == null || accessToken.isBlank()) {
            log.error("MERCADO_PAGO_ACCESS_TOKEN no está configurado. Abortando inicialización de Mercado Pago.");
            throw new IllegalStateException("MERCADO_PAGO_ACCESS_TOKEN no está configurado. Abortando...");
        }
        MercadoPagoConfig.setAccessToken(accessToken);
    }

    public String crearPreferencia(Long reservaId, String pagador, BigDecimal monto) throws MPException, MPApiException {
        PreferenceClient client = new PreferenceClient();

        // 1. Validar y redondear el monto ANTES de usarlo
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Monto inválido o nulo para la preferencia de Mercado Pago. Reserva ID: {}, Monto recibido: {}", reservaId, monto);
            throw new IllegalArgumentException("El monto de la reserva para Mercado Pago debe ser un número positivo y válido.");
        }
        // Redondear a dos decimales, usando RoundingMode.HALF_UP (el más común para moneda)
        monto = monto.setScale(2, RoundingMode.HALF_UP);


        // 2. Construir el Item de la reserva.
        // Aseguramos que 'quantity' es un Integer explícito y no nulo.
        PreferenceItemRequest item = PreferenceItemRequest.builder()
                .id(String.valueOf(reservaId))
                .title("Reserva de cancha #" + reservaId)
                .description("Reserva realizada por " + pagador)
                .quantity(Integer.valueOf(1)) // Asegurar que es un Integer y no un tipo primitivo que podría ser null
                .unitPrice(monto)
                .currencyId("ARS")
                .build();

        List<PreferenceItemRequest> items = new ArrayList<>();
        items.add(item);

        // URLs de redirección luego del pago (apuntando al frontend)
        PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                .success(frontendSuccessUrl)
                .failure(frontendFailureUrl)
                .pending(frontendPendingUrl)
                .build();

        PreferenceRequest preferenceRequest = PreferenceRequest.builder()
                .items(items)
                .backUrls(backUrls)
                .autoReturn("approved") // Redirige automáticamente si el pago se aprueba
                .notificationUrl(notificationUrl) // Usamos la URL configurada con el parámetro
                .externalReference(String.valueOf(reservaId)) // Para poder identificar luego
                .build();

        try {
            Preference preference = client.create(preferenceRequest);
            return preference.getInitPoint();
        } catch (MPApiException e) {
            String errorContent = "No content available";
            if (e.getApiResponse() != null && e.getApiResponse().getContent() != null) {
                errorContent = e.getApiResponse().getContent();
            } else if (e.getMessage() != null) {
                errorContent = e.getMessage();
            }

            log.error("ERROR API Mercado Pago al crear preferencia para reserva ID {}. Status: {}, Mensaje: {}, Contenido de respuesta: {}",
                    reservaId, e.getStatusCode(), e.getMessage(), errorContent, e);
            throw new MPException("Error en la API de Mercado Pago: " + errorContent, e);
        } catch (MPException e) {
            log.error("Error general de Mercado Pago al crear preferencia para reserva ID {}: {}", reservaId, e.getMessage(), e);
            throw new MPException("Error al crear la preferencia de pago: " + e.getMessage(), e);
        }
    }
}