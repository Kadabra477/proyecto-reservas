package com.example.reservafutbol.Controlador;

// --- IMPORTACIONES NECESARIAS ---

import com.example.reservafutbol.DTO.ReservaDTO;
import com.example.reservafutbol.Modelo.Cancha;
import com.example.reservafutbol.Modelo.Reserva;
import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Servicio.CanchaServicio;
import com.example.reservafutbol.Servicio.ReservaServicio;
import com.example.reservafutbol.Servicio.UsuarioServicio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

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

    // --- OBTENER RESERVAS POR CANCHA (Sin cambios) ---
    @GetMapping("/cancha/{canchaId}")
    public ResponseEntity<List<Reserva>> obtenerReservasPorCancha(@PathVariable Long canchaId) {
        // ... (código sin cambios) ...
        log.info("GET /api/reservas/cancha/{}", canchaId);
        List<Reserva> reservas = reservaServicio.listarReservas(canchaId);
        if (reservas.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(reservas);
    }

    // --- OBTENER TODAS (Admin - Sin cambios) ---
    @GetMapping("/admin/todas")
    public ResponseEntity<List<Reserva>> obtenerTodas() {
        // ... (código sin cambios) ...
        log.info("GET /api/reservas/admin/todas");
        return ResponseEntity.ok(reservaServicio.listarTodas());
    }

    // --- OBTENER RESERVA POR ID (Sin cambios) ---
    @GetMapping("/{id}")
    public ResponseEntity<Reserva> obtenerPorId(@PathVariable Long id) {
        // ... (código sin cambios) ...
        log.info("GET /api/reservas/{}", id);
        return reservaServicio.obtenerReserva(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // --- OBTENER RESERVAS DEL USUARIO AUTENTICADO (Sin cambios) ---
    @GetMapping("/usuario")
    public ResponseEntity<List<Reserva>> obtenerPorUsuario(Authentication authentication) {
        // ... (código sin cambios) ...
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Intento de acceso a /api/reservas/usuario sin autenticación");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String username = authentication.getName();
        log.info("GET /api/reservas/usuario para {}", username);
        try {
            List<Reserva> reservas = reservaServicio.obtenerReservasPorUsername(username);
            return ResponseEntity.ok(reservas);
        } catch (UsernameNotFoundException e) {
            log.error("Usuario no encontrado al buscar sus reservas: {}", username);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            log.error("Error buscando reservas para usuario {}: {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    // --- CREAR RESERVA (Endpoint principal - CORREGIDO) ---
    @PostMapping("/crear")
    public ResponseEntity<?> crearReservaConDTO(@RequestBody ReservaDTO dto, Authentication authentication) {
        log.info("POST /api/reservas/crear por usuario {}", authentication.getName());

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado.");
        }
        String username = authentication.getName();

        if (dto.getCanchaId() == null || dto.getFecha() == null || dto.getHora() == null) {
            return ResponseEntity.badRequest().body("Faltan datos requeridos (canchaId, fecha, hora).");
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

        LocalDateTime fechaHora;
        try {
            fechaHora = LocalDateTime.parse(dto.getFecha() + "T" + dto.getHora());
            if (fechaHora.isBefore(LocalDateTime.now())) {
                return ResponseEntity.badRequest().body("No se pueden crear reservas para fechas u horas pasadas.");
            }
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body("Formato de fecha (YYYY-MM-DD) u hora (HH:MM) inválido.");
        }

        Reserva nuevaReserva = new Reserva();
        nuevaReserva.setUsuario(usuario);
        nuevaReserva.setUserEmail(username);
        nuevaReserva.setCancha(cancha);
        nuevaReserva.setFechaHora(fechaHora);

        // --- CORRECCIÓN Error 1: Convertir precio a BigDecimal ---
        if (cancha.getPrecioPorHora() != null) {
            // Asumiendo que getPrecioPorHora() devuelve Double o double
            try {
                nuevaReserva.setPrecio(BigDecimal.valueOf(cancha.getPrecioPorHora())); // Conversión segura
            } catch (NullPointerException | NumberFormatException ex) {
                log.error("Error al convertir precioPorHora de cancha {} a BigDecimal: {}", cancha.getId(), cancha.getPrecioPorHora(), ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error interno al procesar el precio de la cancha.");
            }
        } else {
            log.error("El precio por hora para la cancha {} es null.", cancha.getId());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error interno: Precio de la cancha no definido.");
        }
        // --- FIN CORRECCIÓN Error 1 ---


        nuevaReserva.setCliente(dto.getNombre() != null && dto.getApellido() != null
                ? dto.getNombre().trim() + " " + dto.getApellido().trim()
                : usuario.getNombreCompleto()); // Asume que User tiene getNombreCompleto()
        nuevaReserva.setTelefono(dto.getTelefono() != null ? dto.getTelefono().trim() : null);


        try {
            Reserva reservaGuardada = reservaServicio.crearReserva(nuevaReserva);
            log.info("Reserva creada con ID: {}", reservaGuardada.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(reservaGuardada);
        } catch (IllegalArgumentException e) {
            log.warn("Error al crear reserva: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Error inesperado al guardar reserva:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error interno al guardar la reserva.");
        }
    }


    // --- CONFIRMAR RESERVA (Admin - Sin cambios) ---
    @PutMapping("/{id}/confirmar")
    public ResponseEntity<Reserva> confirmar(@PathVariable Long id) {
        // ... (código sin cambios) ...
        log.info("PUT /api/reservas/{}/confirmar", id);
        try {
            return ResponseEntity.ok(reservaServicio.confirmarReserva(id));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error al confirmar reserva {}: {}", id, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al confirmar reserva", e);
        }
    }

    // --- MARCAR COMO PAGADA (Sin cambios) ---
    @PutMapping("/{id}/marcar-pagada")
    public ResponseEntity<Reserva> marcarPagada(@PathVariable Long id,
                                                @RequestParam String metodoPago,
                                                @RequestParam(required = false) String mercadoPagoPaymentId) {
        // ... (código sin cambios) ...
        log.info("PUT /api/reservas/{}/marcar-pagada - Metodo: {}, MP ID: {}", id, metodoPago, mercadoPagoPaymentId);
        try {
            Reserva reservaPagada = reservaServicio.marcarComoPagada(id, metodoPago, mercadoPagoPaymentId);
            return ResponseEntity.ok(reservaPagada);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error al marcar reserva {} como pagada: {}", id, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al marcar reserva como pagada", e);
        }
    }

    // --- GENERAR EQUIPOS (Sin cambios) ---
    @PutMapping("/{id}/equipos")
    public ResponseEntity<Reserva> generarEquipos(@PathVariable Long id) {
        // ... (código sin cambios) ...
        log.info("PUT /api/reservas/{}/equipos", id);
        try {
            return ResponseEntity.ok(reservaServicio.generarEquipos(id));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error al generar equipos para reserva {}: {}", id, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al generar equipos", e);
        }
    }

    // --- ELIMINAR RESERVA (Sin cambios) ---
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        // ... (código sin cambios) ...
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
}