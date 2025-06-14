package com.example.reservafutbol.Controlador;

import com.example.reservafutbol.Modelo.Complejo;
import com.example.reservafutbol.Servicio.ComplejoServicio;
import com.example.reservafutbol.payload.request.CrearComplejoRequest; // Importar el nuevo DTO
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map; // Para los mapas de canchas, si vienen del DTO de request
import java.time.LocalTime; // Para horarios, si vienen del DTO de request
import java.util.HashMap; // Para inicializar mapas

@RestController
@RequestMapping("/api/complejos")
public class ComplejoControlador {

    private static final Logger log = LoggerFactory.getLogger(ComplejoControlador.class);

    @Autowired
    private ComplejoServicio complejoServicio;

    // Obtener todos los complejos (público)
    @GetMapping
    public ResponseEntity<List<Complejo>> obtenerTodosLosComplejos() {
        log.info("GET /api/complejos - Obteniendo todos los complejos.");
        List<Complejo> complejos = complejoServicio.listarTodosLosComplejos();
        if (complejos.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(complejos);
    }

    // Nuevo endpoint: Obtener complejos del propietario actual (ROLE_COMPLEX_OWNER, ROLE_ADMIN)
    @GetMapping("/mis-complejos")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLEX_OWNER')")
    public ResponseEntity<List<Complejo>> obtenerMisComplejos(Authentication authentication) {
        String username = authentication.getName();
        log.info("GET /api/complejos/mis-complejos - Obteniendo complejos para usuario: {}", username);
        List<Complejo> complejos = complejoServicio.listarComplejosPorPropietario(username);
        if (complejos.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(complejos);
    }

    // Obtener complejo por ID (público)
    @GetMapping("/{id}")
    public ResponseEntity<Complejo> obtenerComplejoPorId(@PathVariable Long id) {
        log.info("GET /api/complejos/{} - Obteniendo complejo por ID.", id);
        return complejoServicio.buscarComplejoPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Crear un nuevo complejo (Solo ADMIN) - Ahora recibe CrearComplejoRequest completo
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Complejo> crearComplejo(@RequestBody CrearComplejoRequest request, Authentication authentication) {
        String adminUsername = authentication.getName(); // El ADMIN que crea el complejo
        log.info("POST /api/complejos - Creando nuevo complejo '{}' para propietario '{}' por ADMIN: {}",
                request.getNombre(), request.getPropietarioUsername(), adminUsername);
        try {
            // El servicio creará el complejo y asignará el propietario y todos los detalles de canchas
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Error inesperado al crear complejo:", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno al crear complejo.");
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (SecurityException e) {
            log.warn("Acceso denegado para actualizar complejo {}: {}", id, e.getMessage());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            log.error("Error inesperado al actualizar complejo {}:", id, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno al actualizar complejo.");
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (SecurityException e) {
            log.warn("Acceso denegado para eliminar complejo {}: {}", id, e.getMessage());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            log.error("Error inesperado al eliminar complejo {}:", id, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno al eliminar complejo.");
        }
    }
}