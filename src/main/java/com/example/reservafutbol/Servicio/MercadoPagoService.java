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
            // Considera lanzar una excepción o deshabilitar la funcionalidad si el token es crítico.
            throw new IllegalStateException("MERCADO_PAGO_ACCESS_TOKEN no está configurado. Abortando...");
        }
        MercadoPagoConfig.setAccessToken(accessToken);
    }

    public String crearPreferencia(Long reservaId, String pagador, BigDecimal monto) throws MPException, MPApiException {
        PreferenceClient client = new PreferenceClient();

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

        PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                .success(frontendSuccessUrl)
                .failure(frontendFailureUrl)
                .pending(frontendPendingUrl)
                .build();

        PreferenceRequest preferenceRequest = PreferenceRequest.builder()
                .items(items)
                .backUrls(backUrls)
                .autoReturn("approved")
                .notificationUrl(notificationUrl)
                .externalReference(String.valueOf(reservaId))
                .build();

        try {
            Preference preference = client.create(preferenceRequest);
            return preference.getInitPoint();
        } catch (MPApiException e) {
            // ¡¡¡CAMBIO CRÍTICO AQUÍ: Loguear el contenido de la respuesta de error de MP!!!
            String errorContent = e.getApiResponse() != null ? e.getApiResponse().getContent() : "No content available";
            log.error("ERROR API Mercado Pago al crear preferencia. Status: {}, Mensaje: {}, Contenido de respuesta: {}",
                    e.getStatusCode(), e.getMessage(), errorContent, e);
            throw new MPException("Error en la API de Mercado Pago: " + errorContent, e); // Pasa el contenido al mensaje
        } catch (MPException e) {
            log.error("Error general de Mercado Pago al crear preferencia: {}", e.getMessage(), e);
            throw new MPException("Error al crear la preferencia de pago", e);
        }
    }
}