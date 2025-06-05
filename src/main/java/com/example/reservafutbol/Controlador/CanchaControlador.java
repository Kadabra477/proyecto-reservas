package com.example.reservafutbol.Controlador;

import com.example.reservafutbol.Modelo.Cancha;
import com.example.reservafutbol.Servicio.CanchaServicio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory; // Asegúrate de importar LoggerFactory

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/canchas")
public class CanchaControlador {

    private static final Logger log = LoggerFactory.getLogger(CanchaControlador.class); // Declaración del logger

    @Autowired
    private CanchaServicio canchaServicio;

    // Obtener todas las canchas (público)
    @GetMapping
    public ResponseEntity<List<Cancha>> obtenerCanchas() {
        log.info("GET /api/canchas - Obteniendo todas las canchas.");
        // CORREGIDO: Cambiado listarCanchas() a listarTodasCanchas()
        List<Cancha> canchas = canchaServicio.listarTodasCanchas();
        if (canchas.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(canchas);
    }

    // Obtener cancha por ID (público)
    @GetMapping("/{id}")
    public ResponseEntity<Cancha> obtenerCanchaPorId(@PathVariable Long id) {
        log.info("GET /api/canchas/{} - Obteniendo cancha por ID.", id);
        return canchaServicio.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Crear una nueva cancha (Solo ADMIN)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Cancha> crearCancha(@RequestBody Cancha cancha) {
        log.info("POST /api/canchas - Creando nueva cancha: {}", cancha.getNombre());
        try {
            Cancha nuevaCancha = canchaServicio.crearCancha(cancha);
            return new ResponseEntity<>(nuevaCancha, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            log.warn("Error al crear cancha: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
            log.error("Error inesperado al crear cancha:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    // Actualizar una cancha existente (Solo ADMIN)
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<Cancha> actualizarCancha(@PathVariable Long id, @RequestBody Cancha cancha) {
        log.info("PUT /api/canchas/{} - Actualizando cancha: {}", id, cancha.getNombre());
        try {
            Cancha canchaActualizada = canchaServicio.actualizarCancha(id, cancha);
            return new ResponseEntity<>(canchaActualizada, HttpStatus.OK);
        }
        // Se puede añadir un manejo específico para NotFound si el error es de ID inexistente
        // } catch (NoSuchElementException e) { // Si el servicio lanza NoSuchElementException
        //     log.warn("Cancha no encontrada para actualizar {}: {}", id, e.getMessage());
        //     return ResponseEntity.notFound().build();
        catch (IllegalArgumentException e) { // Captura si el servicio lanza IllegalArgumentException
            log.warn("Error al actualizar cancha {}: {}", id, e.getMessage());
            // Si el mensaje de error indica que no se encontró la cancha, se envía un 404
            if (e.getMessage().contains("no encontrada")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().body(null); // Para otros errores de validación
        } catch (Exception e) {
            log.error("Error inesperado al actualizar cancha {}:", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    // Eliminar una cancha (Solo ADMIN)
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarCancha(@PathVariable Long id) {
        log.info("DELETE /api/canchas/{} - Eliminando cancha.", id);
        try {
            // CORREGIDO: El método eliminarCancha en el servicio ahora es void y lanza excepción
            canchaServicio.eliminarCancha(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT); // 204 No Content si la eliminación fue exitosa
        } catch (IllegalArgumentException e) { // Captura si la cancha no fue encontrada
            log.warn("Error al eliminar cancha {}: {}", id, e.getMessage());
            return new ResponseEntity<>(HttpStatus.NOT_FOUND); // 404 Not Found
        } catch (Exception e) {
            log.error("Error inesperado al eliminar cancha {}:", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build(); // 500 Internal Server Error
        }
    }
}