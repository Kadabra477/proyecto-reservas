package com.example.reservafutbol.Controlador;

import com.example.reservafutbol.DTO.ReservaDetalleDTO;
import com.example.reservafutbol.DTO.ReservaDTO;
import com.example.reservafutbol.Modelo.Complejo; // Importar Complejo
import com.example.reservafutbol.Modelo.Reserva;
import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Servicio.ComplejoServicio; // Importar ComplejoServicio
import com.example.reservafutbol.Servicio.PdfGeneratorService;
import com.example.reservafutbol.Servicio.ReservaServicio;
import com.example.reservafutbol.Servicio.UsuarioServicio;
import com.itextpdf.text.DocumentException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    private ComplejoServicio complejoServicio; // Inyectar ComplejoServicio

    @Autowired
    private UsuarioServicio usuarioServicio;

    @Autowired(required = false)
    private PdfGeneratorService pdfGeneratorService;

    // ELIMINADO: Endpoint para obtener reservas por canchaId específica (ya no es relevante)
    // @GetMapping("/cancha/{canchaId}")
    // public ResponseEntity<List<Reserva>> obtenerReservasPorCancha(@PathVariable Long canchaId) { ... }

    // NUEVO ENDPOINT: Obtener reservas por complejo y tipo (para uso de admin/internos)
    @GetMapping("/complejo/{complejoId}/tipo/{tipoCancha}")
    public ResponseEntity<List<ReservaDetalleDTO>> obtenerReservasPorComplejoYTipo(
            @PathVariable Long complejoId, @PathVariable String tipoCancha) {
        log.info("GET /api/reservas/complejo/{}/tipo/{} - Obteniendo reservas.", complejoId, tipoCancha);
        // Implementa lógica para obtener reservas de un tipo en un complejo
        // Esto podría ser un nuevo método en ReservaServicio
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build(); // Placeholder
    }


    @GetMapping("/admin/todas")
    public ResponseEntity<List<ReservaDetalleDTO>> obtenerTodas() {
        log.info("GET /api/reservas/admin/todas - Obteniendo todas las reservas (Admin).");
        List<Reserva> reservas = reservaServicio.listarTodas();
        List<ReservaDetalleDTO> reservasDTO = reservas.stream()
                .map(ReservaDetalleDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(reservasDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservaDetalleDTO> obtenerPorId(@PathVariable Long id) {
        log.info("GET /api/reservas/{} - Obteniendo reserva por ID.", id);
        return reservaServicio.obtenerReserva(id)
                .map(ReservaDetalleDTO::new)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/usuario")
    public ResponseEntity<List<ReservaDetalleDTO>> obtenerPorUsuario(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Intento de acceso a /api/reservas/usuario sin autenticación.");
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


    // MODIFICADO: CREAR RESERVA - Ahora recibe complejoId y tipoCancha, el sistema asigna la instancia
    @PostMapping("/crear")
    public ResponseEntity<?> crearReservaConDTO(@RequestBody ReservaDTO dto, Authentication authentication) {
        log.info("POST /api/reservas/crear por usuario {} para complejo ID: {}, tipoCancha: {} en fecha/hora: {}",
                authentication != null ? authentication.getName() : "desconocido",
                dto.getComplejoId(), dto.getTipoCancha(), dto.getFecha() + " " + dto.getHora());

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado.");
        }
        String username = authentication.getName();

        // Validaciones básicas del DTO
        if (dto.getComplejoId() == null || dto.getTipoCancha() == null || dto.getTipoCancha().isBlank() ||
                dto.getFecha() == null || dto.getHora() == null ||
                dto.getMetodoPago() == null || dto.getMetodoPago().isBlank() || dto.getTelefono() == null || dto.getTelefono().isBlank() ||
                dto.getNombre() == null || dto.getNombre().isBlank() || dto.getApellido() == null || dto.getApellido().isBlank()) {
            log.warn("Faltan datos requeridos en el DTO de reserva.");
            return ResponseEntity.badRequest().body("Faltan datos requeridos para la reserva.");
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

        // Obtener el Complejo para asignar a la reserva
        Complejo complejo = complejoServicio.buscarComplejoPorId(dto.getComplejoId())
                .orElseThrow(() -> {
                    log.warn("Complejo no encontrado con ID: {}", dto.getComplejoId());
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "El complejo seleccionado no existe.");
                });

        // Generar el nombre interno de la cancha asignada y obtener el precio
        Optional<String> nombreCanchaAsignadaOpt = reservaServicio.generateAssignedCanchaName(dto.getComplejoId(), dto.getTipoCancha(), dto.getFecha(), dto.getHora());
        if (nombreCanchaAsignadaOpt.isEmpty()) {
            log.warn("No se pudo generar nombre de cancha asignada. Esto indica que no hay slots disponibles.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body("No hay canchas disponibles para el tipo y horario seleccionado. Por favor, elige otro.");
        }
        String nombreCanchaAsignada = nombreCanchaAsignadaOpt.get();

        Double precioPorHora = complejo.getCanchaPrices().get(dto.getTipoCancha());
        if (precioPorHora == null) {
            log.error("Precio no configurado para el tipo de cancha '{}' en complejo ID: {}", dto.getTipoCancha(), complejo.getId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El precio para este tipo de cancha no está configurado en el complejo.");
        }

        // Construir el objeto Reserva con los nuevos campos
        Reserva nuevaReserva = new Reserva();
        nuevaReserva.setUsuario(usuario);
        nuevaReserva.setUserEmail(username);
        nuevaReserva.setComplejo(complejo); // Asignar el objeto Complejo
        nuevaReserva.setTipoCanchaReservada(dto.getTipoCancha()); // Asignar el tipo de cancha reservada
        nuevaReserva.setNombreCanchaAsignada(nombreCanchaAsignada); // Asignar el nombre interno
        nuevaReserva.setFechaHora(LocalDateTime.of(dto.getFecha(), dto.getHora()));
        nuevaReserva.setMetodoPago(dto.getMetodoPago());
        nuevaReserva.setTelefono(dto.getTelefono().trim());
        nuevaReserva.setCliente(dto.getNombre().trim() + " " + dto.getApellido().trim());
        nuevaReserva.setPrecio(BigDecimal.valueOf(precioPorHora)); // Asignar el precio obtenido del complejo

        try {
            Reserva reservaGuardada = reservaServicio.crearReserva(nuevaReserva);
            log.info("Reserva creada con ID: {} para complejo '{}' (tipo: '{}'), asignada a: '{}'",
                    reservaGuardada.getId(), complejo.getNombre(), dto.getTipoCancha(), nombreCanchaAsignada);
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

    // --- Otros Endpoints de Reserva (se mantienen pero pueden usar los nuevos DTOs/relaciones) ---
    @PutMapping("/{id}/confirmar")
    public ResponseEntity<ReservaDetalleDTO> confirmar(@PathVariable Long id) {
        log.info("PUT /api/reservas/{}/confirmar", id);
        try {
            Reserva reservaConfirmada = reservaServicio.confirmarReserva(id);
            return ResponseEntity.ok(new ReservaDetalleDTO(reservaConfirmada));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Error al confirmar reserva {}: {}", id, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al confirmar reserva.");
        }
    }

    @PutMapping("/{id}/marcar-pagada")
    public ResponseEntity<ReservaDetalleDTO> marcarPagada(@PathVariable Long id,
                                                          @RequestParam String metodoPago,
                                                          @RequestParam(required = false) String mercadoPagoPaymentId) {
        log.info("PUT /api/reservas/{}/marcar-pagada - Metodo: {}, MP ID: {}", id, metodoPago, mercadoPagoPaymentId);
        try {
            Reserva reservaPagada = reservaServicio.marcarComoPagada(id, metodoPago, mercadoPagoPaymentId);
            return ResponseEntity.ok(new ReservaDetalleDTO(reservaPagada));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Error al marcar reserva {} como pagada: {}", id, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al marcar reserva como pagada.");
        }
    }

    @PutMapping("/{id}/equipos")
    public ResponseEntity<ReservaDetalleDTO> generarEquipos(@PathVariable Long id) {
        log.info("PUT /api/reservas/{}/equipos", id);
        try {
            Reserva reservaConEquipos = reservaServicio.generarEquipos(id);
            return ResponseEntity.ok(new ReservaDetalleDTO(reservaConEquipos));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Error al generar equipos para reserva {}: {}", id, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al generar equipos.");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        log.info("DELETE /api/reservas/{} - Eliminando reserva.", id);
        try {
            reservaServicio.eliminarReserva(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Error al eliminar reserva {}: {}", id, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al eliminar reserva.");
        }
    }

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
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al generar el comprobante PDF.", e);
        } catch (Exception e) {
            log.error("Error inesperado al generar PDF para reserva ID {}: {}", reservaId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error inesperado al generar el comprobante.", e);
        }
    }

    // MODIFICADO: ELIMINADO el endpoint /cancha/{canchaId}/slots-disponibles
    // Este endpoint ya no es relevante si no hay canchas individuales

    // NUEVO ENDPOINT: Obtener cantidad de canchas disponibles por tipo y horario en un complejo
    @GetMapping("/disponibilidad-por-tipo")
    public ResponseEntity<Integer> getAvailableCanchasCount(
            @RequestParam @NotNull Long complejoId, // ID del complejo es obligatorio
            @RequestParam @NotBlank String tipoCancha, // Tipo de cancha es obligatorio
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime hora) {
        log.info("GET /api/reservas/disponibilidad-por-tipo?complejoId={}&tipoCancha={}&fecha={}&hora={}",
                complejoId, tipoCancha, fecha, hora);
        try {
            int availableCount = reservaServicio.countAvailableCanchasForSlot(complejoId, tipoCancha, fecha, hora);
            return ResponseEntity.ok(availableCount);
        } catch (IllegalArgumentException e) {
            log.warn("Error de validación al obtener disponibilidad por tipo: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Error al obtener cantidad de canchas disponibles para tipo '{}' en complejo ID: {} a {}: {}",
                    tipoCancha, complejoId, fecha + " " + hora, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno al verificar disponibilidad.");
        }
    }
}