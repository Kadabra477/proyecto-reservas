package com.example.reservafutbol.Servicio;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceClient;
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

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoService.class);

    @Value("${MERCADO_PAGO_ACCESS_TOKEN}")
    private String accessToken;

    // Se recomienda mover esta URL a application.properties
    private final String BASE_URL = "http://localhost:8080"; // CAMBIAR en producción

    @PostConstruct
    public void init() {
        log.info("Configurando Mercado Pago SDK...");
        if (accessToken == null || accessToken.isBlank() || accessToken.equals("TEST-xxxxxxxxxxxxxxxxxxxxx")) {
            log.warn("MERCADO_PAGO_ACCESS_TOKEN no está configurado correctamente.");
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

        // URLs de redirección luego del pago
        PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                .success(BASE_URL + "/pagos/success")
                .failure(BASE_URL + "/pagos/failure")
                .pending(BASE_URL + "/pagos/pending")
                .build();

        PreferenceRequest preferenceRequest = PreferenceRequest.builder()
                .items(items)
                .backUrls(backUrls)
                .autoReturn("approved") // Redirige automáticamente si el pago se aprueba
                .notificationUrl("https://9e40-2802-8012-507a-6800-ad8b-26d3-9bf5-fbfe.ngrok-free.app/api/pagos/notificacion\n")
                .externalReference(String.valueOf(reservaId)) // Para poder identificar luego
                .build();

        try {
            Preference preference = client.create(preferenceRequest);
            return preference.getInitPoint();
        } catch (MPApiException | MPException e) {
            log.error("Error al crear la preferencia: {}", e.getMessage(), e);
            throw new MPException("Error al crear la preferencia de pago", e);
        }
    }
}
