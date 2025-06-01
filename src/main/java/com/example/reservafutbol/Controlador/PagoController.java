package com.example.reservafutbol.Controlador;

import com.example.reservafutbol.DTO.PagoDTO;
import com.example.reservafutbol.Modelo.Reserva;
import com.example.reservafutbol.Repositorio.ReservaRepositorio;
import com.example.reservafutbol.Servicio.MercadoPagoService;
import com.example.reservafutbol.Servicio.PagoServicio;
import com.example.reservafutbol.Servicio.ReservaServicio;
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
import java.util.Optional;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private static final Logger log = LoggerFactory.getLogger(PagoController.class);

    @Autowired
    private MercadoPagoService mercadoPagoService;

    @Autowired
    private ReservaServicio reservaServicio;

    @Autowired
    private PagoServicio pagoService;

    @Autowired
    private ReservaRepositorio reservaRepository;

    @PostMapping("/crear-preferencia/{reservaId}")
    public ResponseEntity<Map<String, String>> crearPreferencia(@PathVariable Long reservaId, @RequestBody PagoDTO pagoDTO) {
        log.info("POST /api/pagos/crear-preferencia/{} recibido con DTO: {}", reservaId, pagoDTO);
        Map<String, String> response = new HashMap<>();

        try {
            Optional<Reserva> reservaOptional = reservaServicio.obtenerReserva(reservaId);
            if (reservaOptional.isEmpty()) {
                log.warn("Intento de crear preferencia para reserva no encontrada con ID: {}", reservaId);
                response.put("error", "Reserva no encontrada.");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            Reserva reserva = reservaOptional.get();

            // Usar el monto de la reserva si el DTO no especifica uno válido
            BigDecimal montoFinal = (pagoDTO.getMonto() != null && pagoDTO.getMonto().compareTo(BigDecimal.ZERO) > 0) ? pagoDTO.getMonto() : reserva.getPrecio();
            String pagadorFinal = (pagoDTO.getNombreCliente() != null && !pagoDTO.getNombreCliente().isEmpty()) ? pagoDTO.getNombreCliente() : reserva.getCliente();

            if (montoFinal == null || montoFinal.compareTo(BigDecimal.ZERO) <= 0) {
                log.error("Monto inválido para preferencia de pago de reserva ID {}: {}", reservaId, montoFinal);
                response.put("error", "El monto del pago es inválido.");
                return ResponseEntity.badRequest().body(response);
            }

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
            String errorMessage = e.getApiResponse() != null && e.getApiResponse().getContent() != null
                    ? "Error de la API de Mercado Pago: " + e.getApiResponse().getContent()
                    : "Error de la API de Mercado Pago: " + e.getMessage();
            log.error("ERROR API Mercado Pago al crear preferencia para reserva ID {}: Status={}, Message={}, Response={}",
                    reservaId, e.getStatusCode(), e.getMessage(), e.getApiResponse().getContent(), e);
            response.put("error", errorMessage);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (MPException e) {
            log.error("Error general de Mercado Pago al crear preferencia para reserva ID {}: {}", reservaId, e.getMessage(), e);
            response.put("error", "Error interno al crear preferencia de pago: " + e.getMessage());
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
            if (!"payment".equals(tipo)) {
                log.info("Notificación de tipo no manejado: {}", tipo);
                return ResponseEntity.ok("Tipo no manejado: " + tipo);
            }

            String paymentId = request.getParameter("data.id");
            if (paymentId == null) {
                log.warn("Notificación de Mercado Pago sin 'data.id'. Parámetros: {}", request.getParameterMap());
                return ResponseEntity.badRequest().body("Notificación sin ID de pago.");
            }

            // TODO: Implementar validación de firma de notificación de Mercado Pago para seguridad
            // if (!isValidNotificationSignature(request)) {
            //     log.warn("Firma de notificación de Mercado Pago inválida para paymentId: {}", paymentId);
            //     return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Firma de notificación inválida");
            // }

            JsonNode pagoDetalhes = pagoService.obtenerPagoPorId(paymentId);
            String status = pagoDetalhes.get("status").asText();
            String externalReference = pagoDetalhes.has("external_reference") ? pagoDetalhes.get("external_reference").asText() : null;

            if (externalReference == null) {
                log.error("El pago {} de Mercado Pago no tiene 'external_reference'. No se puede asociar a una reserva.", paymentId);
                return ResponseEntity.badRequest().body("Pago sin referencia externa.");
            }

            // Mercado Pago usa external_reference como el ID de la preferencia.
            // Necesitas buscar la reserva por su preferenceId.
            Reserva reserva = reservaRepository.findByPreferenceId(externalReference);
            if (reserva == null) {
                log.warn("Notificación de pago {} para reserva con external_reference {} (preferenceId) no encontrada.", paymentId, externalReference);
                return ResponseEntity.badRequest().body("Reserva no encontrada para external_reference (preferenceId): " + externalReference);
            }

            log.info("Actualizando estado de reserva {} (preferenceId: {}) a: {}", reserva.getId(), externalReference, status);
            switch (status) {
                case "approved":
                    reserva.setPagada(true);
                    reserva.setEstado("pagada");
                    reserva.setMetodoPago("MercadoPago"); // Mantener el casing consistente
                    reserva.setMercadoPagoPaymentId(paymentId);
                    // Opcional: registrar fecha de pago
                    // reserva.setFechaPago(new Date());
                    break;
                case "pending":
                    reserva.setPagada(false);
                    reserva.setEstado("pendiente_pago_mp"); // O un estado más específico como "in_process"
                    break;
                case "rejected":
                    reserva.setPagada(false);
                    reserva.setEstado("rechazada_pago_mp");
                    break;
                case "in_process":
                    reserva.setPagada(false);
                    reserva.setEstado("pendiente_pago_mp");
                    break;
                case "in_mediation":
                    reserva.setPagada(false);
                    reserva.setEstado("en_mediacion_mp");
                    break;
                default:
                    log.warn("Estado de pago desconocido recibido de Mercado Pago: {}", status);
                    reserva.setEstado("desconocido_mp");
            }
            // La lógica @PreUpdate en la entidad Reserva se encarga de ajustar 'estado' final.
            reservaRepository.save(reserva);
            log.info("Notificación procesada exitosamente para paymentId: {}", paymentId);
            return ResponseEntity.ok("Notificación procesada");

        } catch (Exception e) {
            log.error("Error al procesar la notificación de Mercado Pago", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al procesar notificación");
        }
    }

    // Método dummy, DEBES IMPLEMENTAR LA VALIDACIÓN DE FIRMA REAL PARA PRODUCCIÓN
    private boolean isValidNotificationSignature(HttpServletRequest request) {
        // En un entorno de producción, DEBERÍAS implementar la validación de la firma
        // de las notificaciones IPN de Mercado Pago para asegurarte de que provienen de MP.
        // Ver documentación de MP para esto.
        // Por ahora, se retorna true para permitir el procesamiento.
        return true;
    }

    // Endpoints de redirección de Mercado Pago (frontend se encargará de redirigir a sus propias páginas)
    // Estos endpoints solo loguean y responden. El frontend es quien lee los parámetros y decide la UI.
    @GetMapping("/success")
    public ResponseEntity<String> handleSuccess(@RequestParam Map<String, String> params) {
        log.info("Redirección /pagos/success: {}", params);
        // Aquí no necesitas hacer nada más, el frontend maneja la redirección final
        return ResponseEntity.ok("Callback de éxito recibido en backend.");
    }

    @GetMapping("/failure")
    public ResponseEntity<String> handleFailure(@RequestParam Map<String, String> params) {
        log.warn("Redirección /pagos/failure: {}", params);
        return ResponseEntity.ok("Callback de fallo recibido en backend.");
    }

    @GetMapping("/pending")
    public ResponseEntity<String> handlePending(@RequestParam Map<String, String> params) {
        log.info("Redirección /pagos/pending: {}", params);
        return ResponseEntity.ok("Callback de pendiente recibido en backend.");
    }
}