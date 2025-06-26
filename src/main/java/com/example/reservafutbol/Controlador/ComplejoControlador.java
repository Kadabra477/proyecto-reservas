package com.example.reservafutbol.Controlador;

import com.example.reservafutbol.Modelo.Complejo;
import com.example.reservafutbol.Servicio.ComplejoServicio;
import com.example.reservafutbol.payload.request.CrearComplejoRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/complejos")
public class ComplejoControlador {

    private static final Logger log = LoggerFactory.getLogger(ComplejoControlador.class);

    @Autowired
    private ComplejoServicio complejoServicio;

    // Obtener todos los complejos (público) - ADMIN debe usar este en el frontend
    @GetMapping
    public ResponseEntity<List<Complejo>> obtenerTodosLosComplejos() {
        log.info("GET /api/complejos - Obteniendo todos los complejos.");
        List<Complejo> complejos = complejoServicio.listarTodosLosComplejos(); // Llama a findAll() base
        if (complejos.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(complejos);
    }

    // Obtener complejos del propietario actual (ROLE_COMPLEX_OWNER usa este en el frontend)
    @GetMapping("/mis-complejos")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLEX_OWNER')")
    public ResponseEntity<List<Complejo>> obtenerMisComplejos(Authentication authentication) {
        String username = authentication.getName();
        log.info("GET /api/complejos/mis-complejos - Obteniendo complejos para usuario: {}", username);
        List<Complejo> complejos = complejoServicio.listarComplejosPorPropietario(username); // Llama a findByPropietario() base
        if (complejos.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(complejos);
    }

    // Obtener complejo por ID (público, pero también usado por AdminPanel al editar)
    @GetMapping("/{id}")
    public ResponseEntity<Complejo> obtenerComplejoPorId(@PathVariable Long id) {
        log.info("GET /api/complejos/{} - Obteniendo complejo por ID.", id);
        return complejoServicio.buscarComplejoPorId(id) // Llama a findById() base (EAGER carga el propietario)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Crear un nuevo complejo (Solo ADMIN)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Complejo> crearComplejo(@RequestBody CrearComplejoRequest request, Authentication authentication) {
        String adminUsername = authentication.getName();
        log.info("POST /api/complejos - Creando nuevo complejo '{}' para propietario '{}' por ADMIN: {}",
                request.getNombre(), request.getPropietarioUsername(), adminUsername);
        try {
            Complejo nuevoComplejo = complejoServicio.crearComplejoParaAdmin(
                    request.getNombre(),
                    request.getPropietarioUsername(),
                    request.getDescripcion(),
                    request.getUbicacion(),
                    request.getTelefono(),
                    request.getFotoUrl(),
                    request.getHorarioApertura(),
                    request.getHorarioCierre(),
                    request.getCanchaCounts(),
                    request.getCanchaPrices(),
                    request.getCanchaSurfaces(),
                    request.getCanchaIluminacion(),
                    request.getCanchaTecho()
            );
            return new ResponseEntity<>(nuevoComplejo, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            log.warn("Error al crear complejo: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null); // No ResponseStatusException
        } catch (Exception e) {
            log.error("Error inesperado al crear complejo:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null); // No ResponseStatusException
        }
    }

    // Actualizar un complejo existente (ADMIN o COMPLEX_OWNER del complejo)
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLEX_OWNER')")
    @PutMapping("/{id}")
    public ResponseEntity<Complejo> actualizarComplejo(@PathVariable Long id, @RequestBody Complejo complejo, Authentication authentication) {
        String username = authentication.getName();
        log.info("PUT /api/complejos/{} - Actualizando complejo: {} por usuario: {}", id, complejo.getNombre(), username);
        try {
            Complejo complejoActualizado = complejoServicio.actualizarComplejo(id, complejo, username);
            return new ResponseEntity<>(complejoActualizado, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            log.warn("Error al actualizar complejo {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build(); // No ResponseStatusException
        } catch (SecurityException e) {
            log.warn("Acceso denegado para actualizar complejo {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); // No ResponseStatusException
        } catch (Exception e) {
            log.error("Error inesperado al actualizar complejo {}:", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build(); // No ResponseStatusException
        }
    }

    // Eliminar un complejo (ADMIN o COMPLEX_OWNER del complejo)
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLEX_OWNER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarComplejo(@PathVariable Long id, Authentication authentication) {
        String username = authentication.getName();
        log.info("DELETE /api/complejos/{} - Eliminando complejo por usuario: {}", id, username);
        try {
            complejoServicio.eliminarComplejo(id, username);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (IllegalArgumentException e) {
            log.warn("Error al eliminar complejo {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build(); // No ResponseStatusException
        } catch (SecurityException e) {
            log.warn("Acceso denegado para eliminar complejo {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); // No ResponseStatusException
        } catch (Exception e) {
            log.error("Error inesperado al eliminar complejo {}:", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build(); // No ResponseStatusException
        }
    }
}