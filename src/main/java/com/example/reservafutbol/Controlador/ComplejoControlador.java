package com.example.reservafutbol.Controlador;

import com.example.reservafutbol.Modelo.Complejo;
import com.example.reservafutbol.Servicio.ComplejoServicio;
import com.example.reservafutbol.payload.request.CrearComplejoRequest;
import com.example.reservafutbol.payload.response.ComplejoResponseDTO; // Si usas DTOs para la respuesta
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/complejos")
public class ComplejoControlador {

    private static final Logger log = LoggerFactory.getLogger(ComplejoControlador.class);

    @Autowired
    private ComplejoServicio complejoServicio;

    // Obtener todos los complejos (público) - Ahora devuelve DTOs
    @GetMapping
    public ResponseEntity<List<ComplejoResponseDTO>> obtenerTodosLosComplejos() {
        log.info("GET /api/complejos - Obteniendo todos los complejos.");
        List<Complejo> complejos = complejoServicio.listarTodosLosComplejos();
        if (complejos.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        // Si ComplejoResponseDTO necesita ser actualizado para manejar el mapa de URLs, hazlo.
        // Por ahora, asumo que ComplejoResponseDTO se construye a partir de Complejo.
        List<ComplejoResponseDTO> dtos = complejos.stream()
                .map(ComplejoResponseDTO::new) // Asegúrate que ComplejoResponseDTO pueda manejar el nuevo campo de fotos
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    // Obtener complejos del propietario actual (ROLE_COMPLEX_OWNER usa este en el frontend)
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

    // Obtener complejo por ID (público, pero también usado por AdminPanel al editar)
    @GetMapping("/{id}")
    public ResponseEntity<Complejo> obtenerComplejoPorId(@PathVariable Long id) {
        log.info("GET /api/complejos/{} - Obteniendo complejo por ID.", id);
        return complejoServicio.buscarComplejoPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Endpoint para crear un nuevo complejo.
     * Ahora acepta un array de archivos de imagen (opcional) y los datos del complejo como partes separadas.
     *
     * @param request Datos del complejo (JSON).
     * @param photoFiles Array de archivos de imagen (opcional).
     * @param authentication Información de autenticación del usuario.
     * @return ResponseEntity con el complejo creado o un error.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> crearComplejo(
            @RequestPart("complejo") CrearComplejoRequest request,
            @RequestPart(value = "photos", required = false) MultipartFile[] photoFiles,
            Authentication authentication) {
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
                    photoFiles, // Pasar el array de archivos
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
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            log.error("Error de I/O al procesar la imagen para el complejo:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Error al procesar la imagen: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error inesperado al crear complejo:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Ocurrió un error inesperado al crear el complejo."));
        }
    }

    /**
     * Endpoint para actualizar un complejo existente.
     * Ahora acepta un array de archivos de imagen (opcional) y los datos del complejo como partes separadas.
     *
     * @param id ID del complejo a actualizar.
     * @param complejoDetails Datos actualizados del complejo (JSON).
     * @param photoFiles Array de archivos de imagen (opcional) para actualizar la foto del complejo.
     * @param authentication Información de autenticación del usuario.
     * @return ResponseEntity con el complejo actualizado o un error.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLEX_OWNER')")
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> actualizarComplejo(
            @PathVariable Long id,
            @RequestPart("complejo") Complejo complejoDetails, // Complejo ahora tiene el mapa de URLs
            @RequestPart(value = "photos", required = false) MultipartFile[] photoFiles,
            Authentication authentication) {
        String username = authentication.getName();
        log.info("PUT /api/complejos/{} - Actualizando complejo: {} por usuario: {}", id, complejoDetails.getNombre(), username);
        try {
            // Asegúrate de que complejoDetails.getFotoUrlsPorResolucion() se pase correctamente si el frontend lo envía
            Complejo complejoActualizado = complejoServicio.actualizarComplejo(id, complejoDetails, photoFiles, username);
            return new ResponseEntity<>(complejoActualizado, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            log.warn("Error al actualizar complejo {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (SecurityException e) {
            log.warn("Acceso denegado para actualizar complejo {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            log.error("Error de I/O al procesar la imagen para el complejo ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Error al procesar la imagen: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error inesperado al actualizar complejo {}:", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Ocurrió un error inesperado al actualizar el complejo."));
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
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            log.warn("Acceso denegado para eliminar complejo {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("Error inesperado al eliminar complejo {}:", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
