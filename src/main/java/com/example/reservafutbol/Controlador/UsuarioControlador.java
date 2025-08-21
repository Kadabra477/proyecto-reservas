package com.example.reservafutbol.Controlador;

import com.example.reservafutbol.DTO.PerfilDTO;
import com.example.reservafutbol.Modelo.ERole; // Mantenido si se usa en otras partes
import com.example.reservafutbol.Modelo.Role; // Mantenido si se usa en otras partes
import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Servicio.UsuarioServicio;
import com.example.reservafutbol.Servicio.S3StorageService; // Aún lo necesitamos para Complejo, pero no para Perfil
import com.example.reservafutbol.Repositorio.RoleRepositorio; // Mantenido si es necesaria para otras partes
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile; // Mantenido si se usa para otros uploads (ej. Complejo)
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UsuarioControlador {

    private static final Logger log = LoggerFactory.getLogger(UsuarioControlador.class);

    @Autowired
    private UsuarioServicio usuarioServicio;

    @Autowired
    private S3StorageService s3StorageService; // Inyectado porque lo usas en el controlador de Complejos

    @Autowired(required = false) // 'required = false' para evitar errores si no se encuentra en el contexto
    private RoleRepositorio roleRepositorio; // Puede ser eliminado si solo lo usaba el método duplicado

    @GetMapping("/me")
    public ResponseEntity<?> obtenerPerfil(Authentication auth) {
        Optional<User> optUser = obtenerUsuarioAutenticado(auth);
        if (optUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Debe iniciar sesión.");
        }

        User user = optUser.get();
        List<String> roles = user.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());

        String nombre = "";
        String apellido = "";
        if (user.getNombreCompleto() != null && !user.getNombreCompleto().isBlank()) {
            String[] partesNombre = user.getNombreCompleto().trim().split("\\s+", 2);
            nombre = partesNombre[0];
            if (partesNombre.length > 1) {
                apellido = partesNombre[1];
            }
        }

        PerfilDTO perfilDTO = new PerfilDTO(
                user.getNombreCompleto(),
                nombre,
                apellido,
                user.getUbicacion(),
                user.getEdad(),
                user.getBio(),
                user.getUsername(),
                // ¡Campo 'profilePictureUrl' ELIMINADO de la construcción del DTO!
                roles
        );

        return ResponseEntity.ok(perfilDTO);
    }

    @PutMapping("/me")
    public ResponseEntity<?> actualizarPerfil(@RequestBody PerfilDTO perfilDTO, Authentication auth) {
        Optional<User> optUser = obtenerUsuarioAutenticado(auth);
        if (optUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Debe iniciar sesión.");
        }

        User user = optUser.get();
        usuarioServicio.updateUserProfile(
                user,
                perfilDTO.getNombreCompleto(),
                perfilDTO.getUbicacion(),
                perfilDTO.getEdad(),
                perfilDTO.getBio()
        );

        return ResponseEntity.ok("Perfil actualizado correctamente.");
    }

    // ¡Endpoint 'subirFotoPerfil' ELIMINADO!

    // LISTAR TODOS LOS USUARIOS (Solo ADMIN) - Este está bien y es necesario
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<User>> getAllUsers() {
        log.info("GET /api/users - Obteniendo todos los usuarios (solo ADMIN).");
        List<User> users = usuarioServicio.findAllUsers();
        return ResponseEntity.ok(users);
    }
    // ACTIVAR CUENTA (Solo Admin)
    @PutMapping("/admin/users/{userId}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> activateUser(@PathVariable Long userId) {
        log.info("PUT /api/users/admin/users/{}/activate - Activando usuario.", userId);
        boolean activated = usuarioServicio.activateUser(userId);
        if (activated) {
            return ResponseEntity.ok("Usuario " + userId + " activado exitosamente.");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Error: Usuario con ID " + userId + " no encontrado.");
        }
    }

    // 🆕 NUEVO ENDPOINT PARA ACTUALIZAR ROLES 🆕
    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateRoles(@PathVariable Long userId, @RequestBody List<String> roleNames) {
        log.info("PUT /api/users/{}/roles - Actualizando roles para el usuario.", userId);
        try {
            // Convertir la List<String> a un Set<ERole>
            Set<ERole> desiredRoles = roleNames.stream()
                    .map(ERole::valueOf)
                    .collect(Collectors.toSet());

            // Llamar al método del servicio con el tipo de dato correcto
            usuarioServicio.updateUserRoles(userId, desiredRoles);

            return ResponseEntity.ok("Roles actualizados exitosamente.");
        } catch (IllegalArgumentException e) {
            log.error("Error al actualizar roles: Rol inválido proporcionado", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            log.error("Error inesperado al actualizar roles:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al procesar la solicitud.");
        }
    }

    private Optional<User> obtenerUsuarioAutenticado(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        return usuarioServicio.findByUsername(auth.getName());
    }

}