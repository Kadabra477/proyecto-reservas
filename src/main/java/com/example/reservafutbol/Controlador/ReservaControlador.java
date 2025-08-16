package com.example.reservafutbol.Controlador;

import com.example.reservafutbol.DTO.ReservaDetalleDTO;
import com.example.reservafutbol.DTO.ReservaDTO;
import com.example.reservafutbol.Modelo.Complejo;
import com.example.reservafutbol.Modelo.Reserva;
import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Servicio.ComplejoServicio;
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
import org.springframework.security.access.prepost.PreAuthorize;
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
    private ComplejoServicio complejoServicio;

    @Autowired
    private UsuarioServicio usuarioServicio;

    @Autowired(required = false)
    private PdfGeneratorService pdfGeneratorService;

    // Endpoint para obtener reservas por complejo y tipo (útil para ver slots específicos)
    @GetMapping("/complejo/{complejoId}/tipo/{tipoCancha}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLEX_OWNER')")
    public ResponseEntity<List<ReservaDetalleDTO>> obtenerReservasPorComplejoYTipo(
            @PathVariable Long complejoId, @PathVariable String tipoCancha, Authentication authentication) {
        String username = authentication.getName();
        log.info("GET /api/reservas/complejo/{}/tipo/{} por usuario {}", complejoId, tipoCancha, username);

        try {
            List<Reserva> reservas = reservaServicio.listarReservasPorComplejoYTipo(complejoId, tipoCancha, username);
            List<ReservaDetalleDTO> reservasDTO = reservas.stream()
                    .map(ReservaDetalleDTO::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(reservasDTO);
        } catch (SecurityException e) {
            log.warn("Acceso denegado a reservas de complejo {} por {}: {}", complejoId, username, e.getMessage());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Error al obtener reservas por complejo y tipo: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Error inesperado al obtener reservas por complejo y tipo para {}: {}", username, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno al obtener reservas.");
        }
    }


    @GetMapping("/admin/todas")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLEX_OWNER')")
    public ResponseEntity<List<ReservaDetalleDTO>> obtenerTodas(Authentication authentication) {
        String username = authentication.getName();
        log.info("GET /api/reservas/admin/todas - Obteniendo todas las reservas (filtradas por rol si aplica) para: {}", username);
        try {
            List<Reserva> reservas = reservaServicio.listarTodas(username);
            List<ReservaDetalleDTO> reservasDTO = reservas.stream()
                    .map(ReservaDetalleDTO::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(reservasDTO);
        } catch (UsernameNotFoundException e) {
            log.error("Usuario no encontrado al listar todas las reservas: {}", username);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Error inesperado al listar todas las reservas para {}: {}", username, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno al listar reservas.");
        }
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


    @PostMapping("/crear")
    public ResponseEntity<?> crearReservaConDTO(@RequestBody ReservaDTO dto, Authentication authentication) {
        log.info("POST /api/reservas/crear por usuario {} para complejo ID: {}, tipoCancha: {} en fecha/hora: {}",
                authentication != null ? authentication.getName() : "desconocido",
                dto.getComplejoId(), dto.getTipoCancha(), dto.getFecha() + " " + dto.getHora());

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado.");
        }
        String username = authentication.getName();

        User usuario = usuarioServicio.findByUsername(username)
                .orElseThrow(() -> {
                    log.error("Usuario autenticado '{}' no encontrado en la base de datos.", username);
                    return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error de autenticación interno.");
                });

        Complejo complejo = complejoServicio.buscarComplejoPorId(dto.getComplejoId())
                .orElseThrow(() -> {
                    log.warn("Complejo no encontrado con ID: {}", dto.getComplejoId());
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "El complejo seleccionado no existe.");
                });

        Double precioPorHora = complejo.getCanchaPrices().get(dto.getTipoCancha());
        if (precioPorHora == null) {
            log.error("Precio no configurado para el tipo de cancha '{}' en complejo ID: {}", dto.getTipoCancha(), complejo.getId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El precio para este tipo de cancha no está configurado en el complejo.");
        }

        Reserva nuevaReserva = new Reserva();
        nuevaReserva.setUsuario(usuario);
        nuevaReserva.setUserEmail(username);
        nuevaReserva.setComplejo(complejo);
        nuevaReserva.setTipoCanchaReservada(dto.getTipoCancha());
        nuevaReserva.setFechaHora(LocalDateTime.of(dto.getFecha(), dto.getHora()));
        nuevaReserva.setMetodoPago(dto.getMetodoPago());
        nuevaReserva.setTelefono(dto.getTelefono().trim());

        nuevaReserva.setCliente(dto.getNombre().trim() + " " + dto.getApellido().trim());
        nuevaReserva.setDni(String.valueOf(dto.getDni()));

        nuevaReserva.setPrecio(BigDecimal.valueOf(precioPorHora));

        try {
            Reserva reservaGuardada = reservaServicio.crearReserva(nuevaReserva);
            log.info("Reserva creada con ID: {} para complejo '{}' (tipo: '{}'), asignada a: '{}'",
                    reservaGuardada.getId(), complejo.getNombre(), dto.getTipoCancha(), reservaGuardada.getNombreCanchaAsignada());
            return ResponseEntity.status(HttpStatus.CREATED).body(new ReservaDetalleDTO(reservaGuardada));
        } catch (IllegalArgumentException e) {
            log.warn("Error al crear reserva (validación de slot/cancha): {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("Conflicto de estado/lógica al crear reserva: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            log.error("Error inesperado al guardar reserva:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error interno al guardar la reserva. Por favor, intenta de nuevo.");
        }
    }

    // El endpoint de "confirmar" ahora solo cambia el estado a 'confirmada'.
    @PutMapping("/{id}/confirmar")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLEX_OWNER')")
    public ResponseEntity<ReservaDetalleDTO> confirmar(@PathVariable Long id, Authentication authentication) {
        String username = authentication.getName();
        log.info("PUT /api/reservas/{}/confirmar por usuario: {}", id, username);
        try {
            Reserva reservaConfirmada = reservaServicio.confirmarReserva(id, username);
            return ResponseEntity.ok(new ReservaDetalleDTO(reservaConfirmada));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (SecurityException e) {
            log.warn("Acceso denegado para confirmar reserva {}: {}", id, e.getMessage());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Error al confirmar reserva {}: {}", id, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al confirmar reserva.");
        }
    }

    // **NUEVO ENDPOINT** para marcar una reserva como pagada.
    @PutMapping("/{id}/marcar-pagada")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLEX_OWNER')")
    public ResponseEntity<ReservaDetalleDTO> marcarPagada(@PathVariable Long id,
                                                          @RequestParam String metodoPago,
                                                          @RequestParam(required = false) String mercadoPagoPaymentId,
                                                          Authentication authentication) {
        String username = authentication.getName();
        log.info("PUT /api/reservas/{}/marcar-pagada - Metodo: {}, MP ID: {}, por usuario: {}", id, metodoPago, mercadoPagoPaymentId, username);
        try {
            Reserva reservaPagada = reservaServicio.marcarComoPagada(id, metodoPago, mercadoPagoPaymentId, username);
            return ResponseEntity.ok(new ReservaDetalleDTO(reservaPagada));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (SecurityException e) {
            log.warn("Acceso denegado para marcar pagada reserva {}: {}", id, e.getMessage());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Error al marcar reserva {} como pagada: {}", id, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al marcar reserva como pagada.");
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLEX_OWNER')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Authentication authentication) {
        String username = authentication.getName();
        log.info("DELETE /api/reservas/{} - Eliminando reserva por usuario: {}", id, username);
        try {
            reservaServicio.eliminarReserva(id, username);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (SecurityException e) {
            log.warn("Acceso denegado para eliminar reserva {}: {}", id, e.getMessage());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            log.error("Error al eliminar reserva {}: {}", id, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al eliminar reserva.");
        }
    }

    @GetMapping(value = "/{reservaId}/pdf-comprobante", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLEX_OWNER', 'USER')")
    public ResponseEntity<InputStreamResource> generarComprobantePdf(@PathVariable Long reservaId, Authentication authentication) {
        String username = authentication.getName();
        log.info("GET /api/reservas/{}/pdf-comprobante por usuario: {}", reservaId, username);
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

            User requester = usuarioServicio.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

            boolean isAdmin = requester.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            boolean isOwner = requester.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_COMPLEX_OWNER"));
            boolean isUser = requester.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER"));

            // Reglas de acceso al PDF:
            // - ADMIN puede ver cualquier PDF
            // - COMPLEX_OWNER puede ver PDF de reservas de sus complejos
            // - USER puede ver PDF de sus propias reservas
            if (!isAdmin && !(isOwner && reserva.getComplejo() != null && reserva.getComplejo().getPropietario() != null && reserva.getComplejo().getPropietario().getId().equals(requester.getId())) && !(isUser && reserva.getUsuario() != null && reserva.getUsuario().getId().equals(requester.getId()))) {
                throw new SecurityException("Acceso denegado: No tienes permisos para ver este comprobante.");
            }

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
        } catch (SecurityException e) {
            log.warn("Acceso denegado para PDF de reserva {}: {}", reservaId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            log.error("Error inesperado al generar PDF para reserva ID {}: {}", reservaId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error inesperado al generar el comprobante.", e);
        }
    }

    @GetMapping("/disponibilidad-por-tipo")
    public ResponseEntity<Integer> getAvailableCanchasCount(
            @RequestParam @NotNull Long complejoId,
            @RequestParam @NotBlank String tipoCancha,
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
    @GetMapping("/complejo/mis-reservas")
    @PreAuthorize("hasRole('COMPLEX_OWNER')")
    public ResponseEntity<List<ReservaDetalleDTO>> obtenerReservasDeMiComplejo(Authentication authentication) {
        String username = authentication.getName();
        log.info("GET /api/reservas/complejo/mis-reservas - Obteniendo reservas para el complejo del propietario: {}", username);
        try {
            List<Reserva> reservas = reservaServicio.listarReservasDelPropietario(username);
            List<ReservaDetalleDTO> reservasDTO = reservas.stream()
                    .map(ReservaDetalleDTO::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(reservasDTO);
        } catch (UsernameNotFoundException e) {
            log.error("Propietario no encontrado al listar sus reservas: {}", username);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (SecurityException e) {
            log.warn("Acceso denegado para obtener reservas del complejo del propietario {}: {}", username, e.getMessage());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            log.error("Error inesperado al obtener reservas del complejo para {}: {}", username, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno al obtener reservas.");
        }
    }
}