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
import java.util.ArrayList;
import java.util.List;

@Service
public class MercadoPagoService {

    @Value("${backend.url}")
    private String backendUrl; // URL base de tu backend

    @Value("${frontend.url}")
    private String frontendUrl; // URL base de tu frontend

    @Value("${mercadopago.notification.url}") // Esta ya viene con el ?source_news=webhooks
    private String notificationUrl;

    // URLs específicas para las redirecciones del frontend
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
            throw new IllegalStateException("MERCADO_PAGO_ACCESS_TOKEN no está configurado. Abortando...");
        }
        MercadoPagoConfig.setAccessToken(accessToken);
    }

    public String crearPreferencia(Long reservaId, String pagador, BigDecimal monto) throws MPException, MPApiException {
        PreferenceClient client = new PreferenceClient();

        // Item de la reserva
        PreferenceItemRequest item = PreferenceItemRequest.builder()
                .id(String.valueOf(reservaId))
                .title("Reserva de cancha #" + reservaId)
                .description("Reserva realizada por " + pagador)
                .quantity(1)
                .unitPrice(monto)
                .currencyId("ARS")
                .build();

        List<PreferenceItemRequest> items = new ArrayList<>();
        items.add(item);

        // URLs de redirección luego del pago (ahora apuntando al frontend)
        PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                .success(frontendSuccessUrl) // Redirige al frontend
                .failure(frontendFailureUrl) // Redirige al frontend
                .pending(frontendPendingUrl) // Redirige al frontend
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
            log.error("Error al crear la preferencia con la API de Mercado Pago: Status={}, Message={}, Response={}",
                    e.getStatusCode(), e.getMessage(), e.getApiResponse().getContent(), e); // Loguear más detalles del error
            throw new MPException("Error al crear la preferencia de pago con Mercado Pago", e);
        } catch (MPException e) {
            log.error("Error general al crear la preferencia de Mercado Pago: {}", e.getMessage(), e);
            throw new MPException("Error al crear la preferencia de pago", e);
        }
    }
}