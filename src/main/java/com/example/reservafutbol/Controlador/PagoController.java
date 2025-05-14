package com.example.reservafutbol.Controlador;

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

    @PostMapping("/crear-preferencia")
    public Map<String, String> crearPreferencia(@RequestParam Long reservaId, @RequestParam String pagador, @RequestParam Double monto) {
        Map<String, String> response = new HashMap<>();
        try {
            String initPoint = mercadoPagoService.crearPreferencia(reservaId, pagador, BigDecimal.valueOf(monto));
            response.put("initPoint", initPoint);
            return response;
        } catch (MPException | MPApiException e) {
            log.error("Error al crear preferencia de pago", e);
            response.put("error", "Error al crear preferencia");
            return response;
        }
    }

    @RequestMapping(value = "/notificacion", method = {RequestMethod.POST, RequestMethod.GET})
    public ResponseEntity<String> recibirNotificacion(HttpServletRequest request) {
        log.info("Recibida notificación IPN - Método: {}, Parámetros: {}", request.getMethod(), request.getParameterMap());

        try {
            // Obtener los parámetros de la notificación (Mercado Pago envía en formato application/x-www-form-urlencoded)
            String tipo = request.getParameter("type");
            if (!"payment".equals(tipo)) {
                return ResponseEntity.ok("Tipo no manejado: " + tipo);
            }

            // Obtener los datos de la notificación
            String paymentId = request.getParameter("data[id]");
            String preferenceId = request.getParameter("data[external_reference]");

            // Validar que paymentId y preferenceId no sean nulos
            if (paymentId == null || preferenceId == null) {
                return ResponseEntity.badRequest().body("Información incompleta en la notificación");
            }

            // Verificar la firma de la notificación (si Mercado Pago provee una firma)
            if (!isValidNotificationSignature(request)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Firma de notificación inválida");
            }

            // Consultar el pago a Mercado Pago
            JsonNode pago = pagoService.obtenerPagoPorId(paymentId);
            String status = pago.get("status").asText();

            // Buscar la reserva por preferenceId
            Reserva reserva = reservaRepository.findByPreferenceId(preferenceId);
            if (reserva == null) {
                return ResponseEntity.badRequest().body("Reserva no encontrada");
            }

            // Actualizar el estado de la reserva según el status del pago
            switch (status) {
                case "approved":
                    reserva.setEstado("pagado");
                    break;
                case "pending":
                    reserva.setEstado("pendiente");
                    break;
                case "rejected":
                    reserva.setEstado("rechazado");
                    break;
                default:
                    reserva.setEstado("desconocido");
            }

            reservaRepository.save(reserva);
            return ResponseEntity.ok("Notificación procesada");

        } catch (Exception e) {
            log.error("Error al procesar la notificación", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al procesar notificación");
        }
    }

    // Método para verificar la firma de la notificación (si Mercado Pago la proporciona)
    private boolean isValidNotificationSignature(HttpServletRequest request) {
        // Implementar la lógica para verificar la firma, si Mercado Pago proporciona una.
        // Generalmente, Mercado Pago incluye un encabezado como X-MP-Signature para firmar la notificación.
        String signature = request.getHeader("X-MP-Signature");
        if (signature == null || !isValidSignature(signature)) {
            log.error("Firma inválida para la notificación.");
            return false;
        }
        return true;
    }

    // Método para validar la firma (esto dependerá de cómo Mercado Pago envíe la firma)
    private boolean isValidSignature(String signature) {
        // Implementar la lógica para comparar la firma con la que Mercado Pago provee.
        // Aquí puedes usar una clave secreta o algún algoritmo de verificación de firma.
        return true; // Retornar verdadero si la firma es válida.
    }
}
