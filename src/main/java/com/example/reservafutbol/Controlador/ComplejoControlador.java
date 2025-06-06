package com.example.reservafutbol.Controlador;

import com.example.reservafutbol.Modelo.Complejo;
import com.example.reservafutbol.Servicio.ComplejoServicio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/complejos") // Nuevo endpoint base para complejos
public class ComplejoControlador {

    private static final Logger log = LoggerFactory.getLogger(ComplejoControlador.class);

    @Autowired
    private ComplejoServicio complejoServicio;

    // Obtener todos los complejos (puede ser público para que los usuarios los vean)
    @GetMapping
    public ResponseEntity<List<Complejo>> obtenerTodosLosComplejos() {
        log.info("GET /api/complejos - Obteniendo todos los complejos.");
        List<Complejo> complejos = complejoServicio.listarTodosLosComplejos();
        if (complejos.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(complejos);
    }

    // Obtener complejo por ID (puede ser público)
    @GetMapping("/{id}")
    public ResponseEntity<Complejo> obtenerComplejoPorId(@PathVariable Long id) {
        log.info("GET /api/complejos/{} - Obteniendo complejo por ID.", id);
        return complejoServicio.buscarComplejoPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Crear un nuevo complejo (Solo ADMIN)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Complejo> crearComplejo(@RequestBody Complejo complejo) {
        log.info("POST /api/complejos - Creando nuevo complejo: {}", complejo.getNombre());
        try {
            Complejo nuevoComplejo = complejoServicio.crearComplejo(complejo);
            return new ResponseEntity<>(nuevoComplejo, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            log.warn("Error al crear complejo: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Error inesperado al crear complejo:", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno al crear complejo.");
        }
    }

    // Actualizar un complejo existente (Solo ADMIN)
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<Complejo> actualizarComplejo(@PathVariable Long id, @RequestBody Complejo complejo) {
        log.info("PUT /api/complejos/{} - Actualizando complejo: {}", id, complejo.getNombre());
        try {
            Complejo complejoActualizado = complejoServicio.actualizarComplejo(id, complejo);
            return new ResponseEntity<>(complejoActualizado, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            log.warn("Error al actualizar complejo {}: {}", id, e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage()); // 404 si no encontrado, 400 si otros errores
        } catch (Exception e) {
            log.error("Error inesperado al actualizar complejo {}:", id, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno al actualizar complejo.");
        }
    }

    // Eliminar un complejo (Solo ADMIN)
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarComplejo(@PathVariable Long id) {
        log.info("DELETE /api/complejos/{} - Eliminando complejo.", id);
        try {
            complejoServicio.eliminarComplejo(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (IllegalArgumentException e) {
            log.warn("Error al eliminar complejo {}: {}", id, e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Error inesperado al eliminar complejo {}:", id, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno al eliminar complejo.");
        }
    }
}