package com.example.reservafutbol.Controlador;

import com.example.reservafutbol.DTO.PagoDTO; // Importar el DTO de pago
import com.example.reservafutbol.Modelo.Reserva;
import com.example.reservafutbol.Repositorio.ReservaRepositorio;
import com.example.reservafutbol.Servicio.MercadoPagoService;
import com.example.reservafutbol.Servicio.PagoServicio;
import com.example.reservafutbol.Servicio.ReservaServicio; // Necesitarás este servicio
import com.fasterxml.jackson.databind.JsonNode;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional; // Importar Optional

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private static final Logger log = LoggerFactory.getLogger(PagoController.class);

    @Autowired
    private MercadoPagoService mercadoPagoService;

    @Autowired
    private ReservaServicio reservaServicio; // Inyectar ReservaServicio

    @Autowired
    private PagoServicio pagoService; // Aunque parece ser un servicio para consultar pagos, no para crear

    @Autowired
    private ReservaRepositorio reservaRepository; // Ya inyectado, pero lo usaremos más

    // MODIFICADO: Endpoint para crear la preferencia de pago
    // Recibe reservaId como PathVariable y los datos del pago en el cuerpo (PagoDTO)
    @PostMapping("/crear-preferencia/{reservaId}")
    public ResponseEntity<Map<String, String>> crearPreferencia(@PathVariable Long reservaId, @RequestBody PagoDTO pagoDTO) {
        log.info("POST /api/pagos/crear-preferencia/{} recibido con DTO: {}", reservaId, pagoDTO);
        Map<String, String> response = new HashMap<>();

        try {
            // 1. Obtener la reserva para validar y obtener el monto/pagador
            Optional<Reserva> reservaOptional = reservaServicio.obtenerReserva(reservaId);
            if (reservaOptional.isEmpty()) {
                log.warn("Intento de crear preferencia para reserva no encontrada con ID: {}", reservaId);
                response.put("error", "Reserva no encontrada.");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            Reserva reserva = reservaOptional.get();

            // 2. Validar que el monto del DTO coincida con el de la reserva si es crítico
            // Opcional: Podrías ignorar el monto del DTO y usar siempre el de la reserva si es la fuente de verdad.
            // Por ahora, usaremos el del DTO si viene, o el de la reserva como fallback.
            BigDecimal montoFinal = (pagoDTO.getMonto() != null && pagoDTO.getMonto().compareTo(BigDecimal.ZERO) > 0) ? pagoDTO.getMonto() : reserva.getPrecio();
            String pagadorFinal = (pagoDTO.getNombreCliente() != null && !pagoDTO.getNombreCliente().isEmpty()) ? pagoDTO.getNombreCliente() : reserva.getCliente();

            if (montoFinal == null || montoFinal.compareTo(BigDecimal.ZERO) <= 0) {
                log.error("Monto inválido para preferencia de pago de reserva ID {}: {}", reservaId, montoFinal);
                response.put("error", "El monto del pago es inválido.");
                return ResponseEntity.badRequest().body(response);
            }

            // 3. Crear la preferencia de Mercado Pago
            String initPoint = mercadoPagoService.crearPreferencia(reservaId, pagadorFinal, montoFinal);

            if (initPoint != null && !initPoint.isEmpty()) {
                response.put("initPoint", initPoint);
                log.info("Preferencia de Mercado Pago creada exitosamente para reserva ID {}: {}", reservaId, initPoint);
                return ResponseEntity.ok(response);
            } else {
                log.error("Mercado Pago no devolvió un initPoint para la reserva ID: {}", reservaId);
                response.put("error", "No se recibió el punto de inicio de Mercado Pago.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
        } catch (MPApiException e) {
            log.error("Error de la API de Mercado Pago al crear preferencia para reserva ID {}: Status={}, Message={}, Response={}",
                    reservaId, e.getStatusCode(), e.getMessage(), e.getApiResponse().getContent(), e);
            response.put("error", "Error en la API de Mercado Pago: " + e.getApiResponse().getContent());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response); // Podría ser 400 Bad Request si los datos enviados a MP son inválidos
        } catch (MPException e) {
            log.error("Error general de Mercado Pago al crear preferencia para reserva ID {}: {}", reservaId, e.getMessage(), e);
            response.put("error", "Error interno al crear preferencia de pago.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } catch (Exception e) {
            log.error("Error inesperado al crear preferencia de Mercado Pago para reserva ID {}: {}", reservaId, e.getMessage(), e);
            response.put("error", "Error inesperado al crear preferencia de pago.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @RequestMapping(value = "/notificacion", method = {RequestMethod.POST, RequestMethod.GET})
    public ResponseEntity<String> recibirNotificacion(HttpServletRequest request) {
        log.info("Recibida notificación IPN - Método: {}, Parámetros: {}", request.getMethod(), request.getParameterMap());

        try {
            String tipo = request.getParameter("type");
            // Mercado Pago puede enviar "payment" o "merchant_order"
            // Por simplicidad, nos enfocamos en "payment" por ahora.
            if (!"payment".equals(tipo)) {
                log.info("Notificación de tipo no manejado: {}", tipo);
                return ResponseEntity.ok("Tipo no manejado: " + tipo);
            }

            String paymentId = request.getParameter("data.id"); // Correcto para webhooks
            String preferenceId = request.getParameter("external_reference"); // Este es del payload de la preferencia

            if (paymentId == null) { // La notificación de webhooks de MP envía el ID como data.id
                log.warn("Notificación de Mercado Pago sin 'data.id'. Parámetros: {}", request.getParameterMap());
                return ResponseEntity.badRequest().body("Notificación sin ID de pago.");
            }
            // Si la notificación de MP no envía external_reference directamente en el payload de webhook,
            // necesitas obtenerlo de la consulta al pago.
            // Para webhooks, MP suele enviar `external_reference` si lo configuraste.

            // Verificar la firma de la notificación (MUUY IMPORTANTE para seguridad)
            // Esto es un placeholder, deberías implementar la verificación real
            if (!isValidNotificationSignature(request)) {
                log.warn("Firma de notificación de Mercado Pago inválida para paymentId: {}", paymentId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Firma de notificación inválida");
            }

            // Consultar el pago a Mercado Pago para obtener detalles actualizados
            JsonNode pagoDetalhes = pagoService.obtenerPagoPorId(paymentId);
            String status = pagoDetalhes.get("status").asText(); // "approved", "pending", "rejected"
            String externalReference = pagoDetalhes.has("external_reference") ? pagoDetalhes.get("external_reference").asText() : null;

            if (externalReference == null) {
                log.error("El pago {} de Mercado Pago no tiene 'external_reference'. No se puede asociar a una reserva.", paymentId);
                return ResponseEntity.badRequest().body("Pago sin referencia externa.");
            }

            Reserva reserva = reservaRepository.findByPreferenceId(externalReference); // Busca por external_reference
            if (reserva == null) {
                log.warn("Notificación de pago {} para reserva con external_reference {} no encontrada.", paymentId, externalReference);
                return ResponseEntity.badRequest().body("Reserva no encontrada para external_reference: " + externalReference);
            }

            // Actualizar el estado de la reserva
            log.info("Actualizando estado de reserva {} (external_reference: {}) a: {}", reserva.getId(), externalReference, status);
            switch (status) {
                case "approved":
                    reserva.setPagada(true);
                    reserva.setEstado("pagada"); // Usar el estado "pagada"
                    reserva.setMetodoPago("MercadoPago"); // Asignar el método de pago
                    reserva.setMercadoPagoPaymentId(paymentId); // Guardar el ID del pago de MP
                    // reserva.setFechaPago(new Date()); // Si quieres guardar la fecha de pago
                    break;
                case "pending":
                    reserva.setPagada(false); // No pagada aún
                    reserva.setEstado("pendiente_pago_mp"); // Nuevo estado para pagos MP pendientes
                    break;
                case "rejected":
                    reserva.setPagada(false);
                    reserva.setEstado("rechazada_pago_mp"); // Nuevo estado para pagos MP rechazados
                    break;
                default:
                    log.warn("Estado de pago desconocido recibido de Mercado Pago: {}", status);
                    reserva.setEstado("desconocido_mp");
            }

            reservaRepository.save(reserva);
            log.info("Notificación procesada exitosamente para paymentId: {}", paymentId);
            return ResponseEntity.ok("Notificación procesada");

        } catch (Exception e) {
            log.error("Error al procesar la notificación de Mercado Pago", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al procesar notificación");
        }
    }

    // Método dummy para verificar la firma - DEBES IMPLEMENTARLO REALMENTE
    private boolean isValidNotificationSignature(HttpServletRequest request) {
        // La implementación real de esto implica:
        // 1. Obtener el X-Signature header de la solicitud de Mercado Pago.
        // 2. Usar el secreto de Mercado Pago (Webhook secret) para calcular un HMAC-SHA256
        //    del body de la solicitud junto con el ID de la solicitud y el timestamp.
        // 3. Comparar la firma calculada con la firma recibida.
        // Por ahora, para depuración, devolvemos true. ¡NO USAR ESTO EN PRODUCCIÓN!
        return true;
    }

    // MÉTODOS DE REDIRECCIÓN PARA MERCADO PAGO (OPCIONALES, el frontend los maneja)
    // Estos endpoints serían alcanzados por MP después de un pago
    @GetMapping("/success")
    public ResponseEntity<String> handleSuccess(@RequestParam("collection_id") String collectionId,
                                                @RequestParam("collection_status") String collectionStatus,
                                                @RequestParam("payment_id") String paymentId,
                                                @RequestParam("status") String status,
                                                @RequestParam("external_reference") String externalReference,
                                                @RequestParam("preference_id") String preferenceId,
                                                @RequestParam("site_id") String siteId,
                                                @RequestParam("processing_mode") String processingMode,
                                                @RequestParam("merchant_account_id") String merchantAccountId) {
        log.info("Redirección /pagos/success: collection_id={}, status={}, external_reference={}", collectionId, status, externalReference);
        // Aquí podrías hacer una llamada al servicio para verificar el estado final de la reserva
        // y redirigir al frontend con un mensaje específico.
        // Para simplicidad, estamos redirigiendo directamente desde MP al frontend.
        // Este endpoint es principalmente para que MP tenga una URL de retorno en el backend si no tienes JS.
        return ResponseEntity.ok("Pago exitoso en backend. ID de pago: " + paymentId);
    }

    @GetMapping("/failure")
    public ResponseEntity<String> handleFailure(@RequestParam("collection_id") String collectionId,
                                                @RequestParam("collection_status") String collectionStatus,
                                                @RequestParam("payment_id") String paymentId,
                                                @RequestParam("status") String status,
                                                @RequestParam("external_reference") String externalReference,
                                                @RequestParam("preference_id") String preferenceId,
                                                @RequestParam("site_id") String siteId,
                                                @RequestParam("processing_mode") String processingMode,
                                                @RequestParam("merchant_account_id") String merchantAccountId) {
        log.warn("Redirección /pagos/failure: collection_id={}, status={}, external_reference={}", collectionId, status, externalReference);
        return ResponseEntity.ok("Pago fallido en backend. ID de pago: " + paymentId);
    }

    @GetMapping("/pending")
    public ResponseEntity<String> handlePending(@RequestParam("collection_id") String collectionId,
                                                @RequestParam("collection_status") String collectionStatus,
                                                @RequestParam("payment_id") String paymentId,
                                                @RequestParam("status") String status,
                                                @RequestParam("external_reference") String externalReference,
                                                @RequestParam("preference_id") String preferenceId,
                                                @RequestParam("site_id") String siteId,
                                                @RequestParam("processing_mode") String processingMode,
                                                @RequestParam("merchant_account_id") String merchantAccountId) {
        log.info("Redirección /pagos/pending: collection_id={}, status={}, external_reference={}", collectionId, status, externalReference);
        return ResponseEntity.ok("Pago pendiente en backend. ID de pago: " + paymentId);
    }
}