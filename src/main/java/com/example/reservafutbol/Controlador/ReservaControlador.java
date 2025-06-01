package com.example.reservafutbol.Controlador;

import com.example.reservafutbol.DTO.ReservaDetalleDTO;
import com.example.reservafutbol.DTO.ReservaDTO;
import com.example.reservafutbol.Modelo.Cancha;
import com.example.reservafutbol.Modelo.Reserva;
import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Servicio.CanchaServicio;
import com.example.reservafutbol.Servicio.PdfGeneratorService;
import com.example.reservafutbol.Servicio.ReservaServicio;
import com.example.reservafutbol.Servicio.UsuarioServicio;
import com.itextpdf.text.DocumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

    @Autowired(required = false) // Hacerlo opcional si no siempre está configurado (ej. para tests)
    private PdfGeneratorService pdfGeneratorService;

    // --- OBTENER RESERVAS POR CANCHA ---
    @GetMapping("/cancha/{canchaId}")
    public ResponseEntity<List<Reserva>> obtenerReservasPorCancha(@PathVariable Long canchaId) {
        log.info("GET /api/reservas/cancha/{}", canchaId);
        List<Reserva> reservas = reservaServicio.listarReservas(canchaId);
        if (reservas.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(reservas);
    }

    // --- OBTENER TODAS (Admin) ---
    // Usar @PreAuthorize en SecurityConfig o directamente aquí para rol ADMIN
    @GetMapping("/admin/todas")
    // @PreAuthorize("hasRole('ADMIN')") // Si prefieres la seguridad directamente en el controlador
    public ResponseEntity<List<ReservaDetalleDTO>> obtenerTodas() { // Retornar DTO para consistencia
        log.info("GET /api/reservas/admin/todas");
        List<Reserva> reservas = reservaServicio.listarTodas();
        List<ReservaDetalleDTO> reservasDTO = reservas.stream()
                .map(ReservaDetalleDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(reservasDTO);
    }

    // --- OBTENER RESERVA POR ID ---
    @GetMapping("/{id}")
    public ResponseEntity<ReservaDetalleDTO> obtenerPorId(@PathVariable Long id) { // Retornar DTO
        log.info("GET /api/reservas/{}", id);
        return reservaServicio.obtenerReserva(id)
                .map(ReservaDetalleDTO::new) // Convertir a DTO
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // --- OBTENER RESERVAS DEL USUARIO AUTENTICADO ---
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


    // --- CREAR RESERVA (MODIFICADO) ---
    @PostMapping("/crear")
    public ResponseEntity<?> crearReservaConDTO(@RequestBody ReservaDTO dto, Authentication authentication) {
        log.info("POST /api/reservas/crear por usuario {}", authentication != null ? authentication.getName() : "desconocido");

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado.");
        }
        String username = authentication.getName();

        if (dto.getCanchaId() == null || dto.getFecha() == null || dto.getHora() == null || dto.getMetodoPago() == null || dto.getTelefono() == null || dto.getNombre() == null || dto.getApellido() == null) {
            return ResponseEntity.badRequest().body("Faltan datos requeridos (canchaId, fecha, hora, metodoPago, nombre, apellido, telefono).");
        }
        // Validar DNI si es necesario
        if (dto.getDni() == null || !dto.getDni().matches("^\\d{7,8}$")) {
            return ResponseEntity.badRequest().body("El DNI debe contener 7 u 8 dígitos numéricos.");
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

        // Validar si la cancha está disponible
        if (Boolean.FALSE.equals(cancha.getDisponible())) {
            log.warn("Intento de reservar cancha no disponible: {}", cancha.getNombre());
            return ResponseEntity.badRequest().body("Esta cancha no está disponible para reservas en este momento.");
        }

        LocalDateTime fechaHoraSolicitada = LocalDateTime.of(dto.getFecha(), dto.getHora());
        LocalDateTime now = LocalDateTime.now();

        // Validación de fecha y hora más robusta
        if (fechaHoraSolicitada.isBefore(now)) {
            log.warn("Intento de crear reserva para fecha/hora pasada. Solicitada: {}, Ahora: {}", fechaHoraSolicitada, now);
            return ResponseEntity.badRequest().body("No se pueden crear reservas para fechas u horas pasadas.");
        }

        Reserva nuevaReserva = new Reserva();
        nuevaReserva.setUsuario(usuario);
        nuevaReserva.setUserEmail(username);
        nuevaReserva.setCancha(cancha);
        nuevaReserva.setCanchaNombre(cancha.getNombre()); // Asignar el nombre de la cancha
        nuevaReserva.setFechaHora(fechaHoraSolicitada);
        nuevaReserva.setMetodoPago(dto.getMetodoPago());
        nuevaReserva.setTelefono(dto.getTelefono().trim());

        // Combina nombre y apellido para el campo 'cliente'
        String nombreClienteCompleto = dto.getNombre().trim() + " " + dto.getApellido().trim();
        nuevaReserva.setCliente(nombreClienteCompleto);

        // Asigna el precio total desde la cancha
        if (cancha.getPrecioPorHora() != null) {
            nuevaReserva.setPrecio(BigDecimal.valueOf(cancha.getPrecioPorHora()));
        } else {
            log.error("El precio por hora para la cancha {} es null.", cancha.getId());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error interno: Precio de la cancha no definido.");
        }

        try {
            // La lógica de estado inicial (confirmada/pagada) antes de guardar
            if ("efectivo".equalsIgnoreCase(dto.getMetodoPago())) {
                nuevaReserva.setConfirmada(false); // Por defecto efectivo no está confirmado por admin aún
                nuevaReserva.setPagada(false);
                nuevaReserva.setEstado("pendiente"); // Estado inicial pendiente de confirmación (si admin debe confirmarla)
            } else { // Para Mercado Pago
                nuevaReserva.setConfirmada(false); // MP siempre espera confirmación externa del pago
                nuevaReserva.setPagada(false);
                nuevaReserva.setEstado("pendiente_pago_mp");
            }
            // La lógica @PrePersist en la entidad Reserva ajustará el campo 'estado' si es necesario.

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

    // --- CONFIRMAR RESERVA ---
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

    // --- MARCAR COMO PAGADA (para uso interno/admin) ---
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

    // --- GENERAR EQUIPOS ---
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

    // --- ELIMINAR RESERVA ---
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
        if (pdfGeneratorService == null) {
            log.error("PdfGeneratorService no está disponible. No se puede generar PDF.");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "El servicio de generación de PDF no está disponible.");
        }
        try {
            Optional<Reserva> reservaOptional = reservaServicio.obtenerReserva(reservaId);
            if (reservaOptional.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva no encontrada con ID: " + reservaId);
            }
            Reserva reserva = reservaOptional.get();

            ByteArrayInputStream bis = pdfGeneratorService.generarPDFReserva(reserva);

            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition", "inline; filename=comprobante_reserva_" + reservaId + ".pdf");

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