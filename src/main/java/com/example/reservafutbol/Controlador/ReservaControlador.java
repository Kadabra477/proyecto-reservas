package com.example.reservafutbol.Controlador;

import com.example.reservafutbol.DTO.ReservaDetalleDTO;
import com.example.reservafutbol.DTO.ReservaDTO;
import com.example.reservafutbol.Modelo.Cancha;
import com.example.reservafutbol.Modelo.Reserva;
import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Servicio.CanchaServicio;
import com.example.reservafutbol.Servicio.PdfGeneratorService; // Importar el servicio de PDF
import com.example.reservafutbol.Servicio.ReservaServicio;
import com.example.reservafutbol.Servicio.UsuarioServicio;
import com.itextpdf.text.DocumentException; // Importar DocumentException para el PDF
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource; // Para servir archivos
import org.springframework.http.HttpHeaders; // Para headers de respuesta
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType; // Para tipos de medios
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream; // Para el stream del PDF
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reservas")
public class ReservaControlador {

    private static final Logger log = LoggerFactory.getLogger(ReservaControlador.class);

    @Autowired
    private ReservaServicio reservaServicio;

    @Autowired
    private CanchaServicio canchaServicio;

    @Autowired
    private UsuarioServicio usuarioServicio;

    @Autowired
    private PdfGeneratorService pdfGeneratorService; // Inyectar el servicio de PDF

    // ... (Métodos existentes: obtenerReservasPorCancha, obtenerTodas, obtenerPorId, obtenerPorUsuario) ...

    // --- OBTENER RESERVAS POR CANCHA (Sin cambios relevantes si no hay lazy loading issues aquí) ---
    @GetMapping("/cancha/{canchaId}")
    public ResponseEntity<List<Reserva>> obtenerReservasPorCancha(@PathVariable Long canchaId) {
        log.info("GET /api/reservas/cancha/{}", canchaId);
        List<Reserva> reservas = reservaServicio.listarReservas(canchaId);
        if (reservas.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(reservas);
    }

    // --- OBTENER TODAS (Admin - SIN CAMBIOS EN RETORNO, PERO EL SERVICIO AHORA CARGA EAGERLY) ---
    @GetMapping("/admin/todas")
    public ResponseEntity<List<Reserva>> obtenerTodas() {
        log.info("GET /api/reservas/admin/todas");
        return ResponseEntity.ok(reservaServicio.listarTodas());
    }

    // --- OBTENER RESERVA POR ID (Sin cambios relevantes) ---
    @GetMapping("/{id}")
    public ResponseEntity<Reserva> obtenerPorId(@PathVariable Long id) {
        log.info("GET /api/reservas/{}", id);
        return reservaServicio.obtenerReserva(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // --- OBTENER RESERVAS DEL USUARIO AUTENTICADO (MODIFICADO para usar ReservaDetalleDTO) ---
    @GetMapping("/usuario")
    public ResponseEntity<List<ReservaDetalleDTO>> obtenerPorUsuario(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Intento de acceso a /api/reservas/usuario sin autenticación");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String username = authentication.getName();
        log.info("GET /api/reservas/usuario para {}", username);
        try {
            List<Reserva> reservas = reservaServicio.obtenerReservasPorUsername(username);
            List<ReservaDetalleDTO> reservasDTO = reservas.stream()
                    .map(ReservaDetalleDTO::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(reservasDTO);
        } catch (UsernameNotFoundException e) {
            log.error("Usuario no encontrado al buscar sus reservas: {}", username);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            log.error("Error buscando reservas para usuario {}: {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    // --- CREAR RESERVA (MODIFICADO para recibir metodoPago) ---
    @PostMapping("/crear")
    public ResponseEntity<?> crearReservaConDTO(@RequestBody ReservaDTO dto, Authentication authentication) {
        log.info("POST /api/reservas/crear por usuario {}", authentication.getName());

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado.");
        }
        String username = authentication.getName();

        if (dto.getCanchaId() == null || dto.getFecha() == null || dto.getHora() == null || dto.getMetodoPago() == null) {
            return ResponseEntity.badRequest().body("Faltan datos requeridos (canchaId, fecha, hora, metodoPago).");
        }

        Optional<Cancha> canchaOpt = canchaServicio.buscarPorId(dto.getCanchaId());
        Optional<User> usuarioOpt = usuarioServicio.findByUsername(username);

        if (canchaOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Cancha no encontrada.");
        }
        if (usuarioOpt.isEmpty()) {
            log.error("Usuario autenticado '{}' no encontrado en la base de datos.", username);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error de autenticación interno.");
        }

        Cancha cancha = canchaOpt.get();
        User usuario = usuarioOpt.get();

        LocalDateTime fechaHora = LocalDateTime.of(dto.getFecha(), dto.getHora());

        if (fechaHora.isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body("No se pueden crear reservas para fechas u horas pasadas.");
        }

        Reserva nuevaReserva = new Reserva();
        nuevaReserva.setUsuario(usuario);
        nuevaReserva.setUserEmail(username);
        nuevaReserva.setCancha(cancha);
        nuevaReserva.setFechaHora(fechaHora);
        nuevaReserva.setMetodoPago(dto.getMetodoPago()); // Setear el método de pago

        if (cancha.getPrecioPorHora() != null) {
            try {
                nuevaReserva.setPrecio(BigDecimal.valueOf(cancha.getPrecioPorHora()));
            } catch (NullPointerException | NumberFormatException ex) {
                log.error("Error al convertir precioPorHora de cancha {} a BigDecimal: {}", cancha.getId(), cancha.getPrecioPorHora(), ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error interno al procesar el precio de la cancha.");
            }
        } else {
            log.error("El precio por hora para la cancha {} es null.", cancha.getId());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error interno: Precio de la cancha no definido.");
        }

        nuevaReserva.setCliente(dto.getNombre() != null && dto.getApellido() != null && !dto.getNombre().trim().isEmpty()
                ? dto.getNombre().trim() + " " + dto.getApellido().trim()
                : usuario.getNombreCompleto());
        nuevaReserva.setTelefono(dto.getTelefono() != null ? dto.getTelefono().trim() : null);

        try {
            // Si el pago es en efectivo, la reserva se marca como confirmada pero no pagada inicialmente
            if ("efectivo".equalsIgnoreCase(dto.getMetodoPago())) {
                nuevaReserva.setConfirmada(true); // Se confirma directamente al ser efectivo
                nuevaReserva.setPagada(false); // No está pagada aún
                nuevaReserva.setEstado("confirmada_efectivo"); // Un estado específico para efectivo
            } else { // Para Mercado Pago o cualquier otro método por defecto
                nuevaReserva.setConfirmada(false); // Las reservas de MP esperan la confirmación del pago
                nuevaReserva.setPagada(false);
                nuevaReserva.setEstado("pendiente_pago"); // Estado inicial para MP
            }

            Reserva reservaGuardada = reservaServicio.crearReserva(nuevaReserva);
            log.info("Reserva creada con ID: {}", reservaGuardada.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(new ReservaDetalleDTO(reservaGuardada));
        } catch (IllegalArgumentException e) {
            log.warn("Error al crear reserva: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Error inesperado al guardar reserva:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error interno al guardar la reserva.");
        }
    }

    // --- CONFIRMAR RESERVA (MODIFICADO para devolver DTO) ---
    @PutMapping("/{id}/confirmar")
    public ResponseEntity<ReservaDetalleDTO> confirmar(@PathVariable Long id) {
        log.info("PUT /api/reservas/{}/confirmar", id);
        try {
            Reserva reservaConfirmada = reservaServicio.confirmarReserva(id);
            return ResponseEntity.ok(new ReservaDetalleDTO(reservaConfirmada));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error al confirmar reserva {}: {}", id, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al confirmar reserva", e);
        }
    }

    // --- MARCAR COMO PAGADA (MODIFICADO para devolver DTO) ---
    @PutMapping("/{id}/marcar-pagada")
    public ResponseEntity<ReservaDetalleDTO> marcarPagada(@PathVariable Long id,
                                                          @RequestParam String metodoPago,
                                                          @RequestParam(required = false) String mercadoPagoPaymentId) {
        log.info("PUT /api/reservas/{}/marcar-pagada - Metodo: {}, MP ID: {}", id, metodoPago, mercadoPagoPaymentId);
        try {
            Reserva reservaPagada = reservaServicio.marcarComoPagada(id, metodoPago, mercadoPagoPaymentId);
            return ResponseEntity.ok(new ReservaDetalleDTO(reservaPagada));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error al marcar reserva {} como pagada: {}", id, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al marcar reserva como pagada", e);
        }
    }

    // --- GENERAR EQUIPOS (MODIFICADO para devolver DTO) ---
    @PutMapping("/{id}/equipos")
    public ResponseEntity<ReservaDetalleDTO> generarEquipos(@PathVariable Long id) {
        log.info("PUT /api/reservas/{}/equipos", id);
        try {
            Reserva reservaConEquipos = reservaServicio.generarEquipos(id);
            return ResponseEntity.ok(new ReservaDetalleDTO(reservaConEquipos));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error al generar equipos para reserva {}: {}", id, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al generar equipos", e);
        }
    }

    // --- ELIMINAR RESERVA (MODIFICADO para devolver 204 No Content) ---
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        log.info("DELETE /api/reservas/{}", id);
        try {
            reservaServicio.eliminarReserva(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error al eliminar reserva {}: {}", id, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al eliminar reserva", e);
        }
    }

    // NUEVO ENDPOINT: Generar PDF del comprobante de reserva
    @GetMapping(value = "/{reservaId}/pdf-comprobante", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<InputStreamResource> generarComprobantePdf(@PathVariable Long reservaId) {
        log.info("GET /api/reservas/{}/pdf-comprobante", reservaId);
        try {
            Optional<Reserva> reservaOptional = reservaServicio.obtenerReserva(reservaId);
            if (reservaOptional.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva no encontrada con ID: " + reservaId);
            }
            Reserva reserva = reservaOptional.get();

            ByteArrayInputStream bis = pdfGeneratorService.generarPDFReserva(reserva);

            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition", "inline; filename=comprobante_reserva_" + reservaId + ".pdf"); // inline para mostrar en navegador

            return ResponseEntity
                    .ok()
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(new InputStreamResource(bis));

        } catch (DocumentException e) {
            log.error("Error al generar PDF para reserva ID {}: {}", reservaId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al generar el comprobante PDF", e);
        } catch (Exception e) {
            log.error("Error inesperado al generar PDF para reserva ID {}: {}", reservaId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error inesperado al generar el comprobante", e);
        }
    }
}