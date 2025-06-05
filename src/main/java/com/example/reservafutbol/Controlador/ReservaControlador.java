package com.example.reservafutbol.Controlador;

import com.example.reservafutbol.DTO.ReservaDetalleDTO;
import com.example.reservafutbol.DTO.ReservaDTO;
import com.example.reservafutbol.Modelo.Cancha; // Importar Cancha
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
import org.springframework.format.annotation.DateTimeFormat;
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
    private CanchaServicio canchaServicio; // Asegúrate de que CanchaServicio está inyectado

    @Autowired
    private UsuarioServicio usuarioServicio;

    @Autowired(required = false)
    private PdfGeneratorService pdfGeneratorService;

    // --- OBTENER RESERVAS POR CANCHA ESPECÍFICA (se mantiene para compatibilidad con el Admin Panel) ---
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
    @GetMapping("/admin/todas")
    public ResponseEntity<List<ReservaDetalleDTO>> obtenerTodas() {
        log.info("GET /api/reservas/admin/todas");
        List<Reserva> reservas = reservaServicio.listarTodas();
        List<ReservaDetalleDTO> reservasDTO = reservas.stream()
                .map(ReservaDetalleDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(reservasDTO);
    }

    // --- OBTENER RESERVA POR ID ---
    @GetMapping("/{id}")
    public ResponseEntity<ReservaDetalleDTO> obtenerPorId(@PathVariable Long id) {
        log.info("GET /api/reservas/{}", id);
        return reservaServicio.obtenerReserva(id)
                .map(ReservaDetalleDTO::new)
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


    // --- CREAR RESERVA (MODIFICADO: Ahora recibe tipoCancha y asigna una automáticamente) ---
    @PostMapping("/crear")
    public ResponseEntity<?> crearReservaConDTO(@RequestBody ReservaDTO dto, Authentication authentication) {
        log.info("POST /api/reservas/crear por usuario {} para tipoCancha: {} en fecha/hora: {}",
                authentication != null ? authentication.getName() : "desconocido",
                dto.getTipoCancha(),
                dto.getFecha() + " " + dto.getHora());

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado.");
        }
        String username = authentication.getName();

        // Validaciones de datos básicos del DTO
        if (dto.getTipoCancha() == null || dto.getTipoCancha().isBlank() || dto.getFecha() == null || dto.getHora() == null ||
                dto.getMetodoPago() == null || dto.getMetodoPago().isBlank() || dto.getTelefono() == null || dto.getTelefono().isBlank() ||
                dto.getNombre() == null || dto.getNombre().isBlank() || dto.getApellido() == null || dto.getApellido().isBlank()) {
            log.warn("Faltan datos requeridos en el DTO de reserva.");
            return ResponseEntity.badRequest().body("Faltan datos requeridos (tipo de cancha, fecha, hora, método de pago, nombre, apellido, teléfono).");
        }
        if (dto.getDni() == null || !dto.getDni().matches("^\\d{7,8}$")) {
            log.warn("Formato de DNI inválido: {}", dto.getDni());
            return ResponseEntity.badRequest().body("El DNI debe contener 7 u 8 dígitos numéricos.");
        }

        // Buscar el usuario autenticado
        User usuario = usuarioServicio.findByUsername(username)
                .orElseThrow(() -> {
                    log.error("Usuario autenticado '{}' no encontrado en la base de datos.", username);
                    return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error de autenticación interno.");
                });

        // Lógica para encontrar una CANCHA ESPECÍFICA disponible de ese TIPO en ese HORARIO
        Optional<Cancha> canchaAsignadaOpt = reservaServicio.findFirstAvailableCancha(dto.getTipoCancha(), dto.getFecha(), dto.getHora());

        if (canchaAsignadaOpt.isEmpty()) {
            log.warn("No hay canchas de tipo '{}' disponibles para la fecha {} y hora {}. Todas ocupadas.", dto.getTipoCancha(), dto.getFecha(), dto.getHora());
            // HttpStatus.CONFLICT es apropiado aquí si el slot se "agotó"
            return ResponseEntity.status(HttpStatus.CONFLICT).body("No hay canchas disponibles para el tipo y horario seleccionado. Por favor, elige otro.");
        }

        Cancha canchaAsignada = canchaAsignadaOpt.get();

        // Construir el objeto Reserva (ahora con la cancha asignada)
        Reserva nuevaReserva = new Reserva();
        nuevaReserva.setUsuario(usuario);
        nuevaReserva.setUserEmail(username);
        nuevaReserva.setCancha(canchaAsignada); // ASIGNAR LA CANCHA ENCONTRADA
        nuevaReserva.setCanchaNombre(canchaAsignada.getNombre()); // Asignar el nombre de la cancha asignada
        nuevaReserva.setFechaHora(LocalDateTime.of(dto.getFecha(), dto.getHora()));
        nuevaReserva.setMetodoPago(dto.getMetodoPago());
        nuevaReserva.setTelefono(dto.getTelefono().trim());
        nuevaReserva.setCliente(dto.getNombre().trim() + " " + dto.getApellido().trim());
        // El precio se obtiene de la cancha asignada, asegurando que sea el correcto
        if (canchaAsignada.getPrecioPorHora() != null) {
            nuevaReserva.setPrecio(BigDecimal.valueOf(canchaAsignada.getPrecioPorHora()));
        } else {
            log.error("La cancha asignada {} (ID: {}) no tiene precio por hora definido.", canchaAsignada.getNombre(), canchaAsignada.getId());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error interno: Precio de la cancha no definido.");
        }

        try {
            // Llama al servicio para crear la reserva, que ahora incluye las validaciones de disponibilidad
            Reserva reservaGuardada = reservaServicio.crearReserva(nuevaReserva); // El servicio ahora sólo guarda la reserva
            log.info("Reserva creada con ID: {} para cancha {} (tipo: {})", reservaGuardada.getId(), canchaAsignada.getNombre(), dto.getTipoCancha());
            return ResponseEntity.status(HttpStatus.CREATED).body(new ReservaDetalleDTO(reservaGuardada));
        } catch (IllegalArgumentException e) {
            log.warn("Error al crear reserva (validación de slot/cancha): {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("Conflicto de estado/lógica al crear reserva: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
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
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
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

    // NUEVO ENDPOINT: Generar PDF del comprobante de reserva (se mantiene)
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

    // Endpoint para obtener slots disponibles para una cancha específica (se mantiene para AdminPanel o si se usa)
    @GetMapping("/{canchaId}/slots-disponibles")
    public ResponseEntity<List<String>> getAvailableTimeSlots(
            @PathVariable Long canchaId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        log.info("GET /api/reservas/{}/slots-disponibles?fecha={}", canchaId, fecha);
        try {
            List<String> availableSlots = reservaServicio.getAvailableTimeSlots(canchaId, fecha);
            return ResponseEntity.ok(availableSlots);
        } catch (Exception e) {
            log.error("Error al obtener slots disponibles para cancha {} en {}: {}", canchaId, fecha, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // NUEVO ENDPOINT: Obtener cantidad de canchas disponibles por tipo y horario
    @GetMapping("/disponibilidad-por-tipo")
    public ResponseEntity<Integer> getAvailableCanchasCount(
            @RequestParam String tipoCancha,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime hora) {
        log.info("GET /api/reservas/disponibilidad-por-tipo?tipoCancha={}&fecha={}&hora={}", tipoCancha, fecha, hora);
        try {
            int availableCount = reservaServicio.countAvailableCanchasForSlot(tipoCancha, fecha, hora);
            return ResponseEntity.ok(availableCount);
        } catch (Exception e) {
            log.error("Error al obtener cantidad de canchas disponibles para tipo '{}' en {} a {}: {}", tipoCancha, fecha, hora, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}