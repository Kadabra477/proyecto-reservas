package com.example.reservafutbol.Controlador;

import com.example.reservafutbol.Modelo.Cancha;
import com.example.reservafutbol.Servicio.CanchaServicio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/canchas")
public class CanchaControlador {

    private static final Logger log = LoggerFactory.getLogger(CanchaControlador.class);

    @Autowired
    private CanchaServicio canchaServicio;

    // Obtener todas las canchas (público)
    @GetMapping
    public ResponseEntity<List<Cancha>> obtenerCanchas() {
        log.info("GET /api/canchas - Obteniendo todas las canchas.");
        List<Cancha> canchas = canchaServicio.listarCanchas();
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
            return ResponseEntity.badRequest().body(null); // O un DTO de error si lo tienes
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
        } catch (IllegalArgumentException e) {
            log.warn("Error al actualizar cancha {}: {}", id, e.getMessage());
            // Si la cancha no existe, es un 404. Si hay problemas con los datos, es 400.
            if (e.getMessage().contains("no encontrada")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().body(null);
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
            boolean eliminado = canchaServicio.eliminarCancha(id);
            if (eliminado) {
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            }
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (IllegalArgumentException e) {
            log.warn("Error al eliminar cancha {}: {}", id, e.getMessage());
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            log.error("Error inesperado al eliminar cancha {}:", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}