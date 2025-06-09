package com.example.reservafutbol.Controlador;

import com.example.reservafutbol.DTO.PerfilDTO;
import com.example.reservafutbol.Modelo.ERole;
import com.example.reservafutbol.Modelo.Role;
import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Servicio.UsuarioServicio;
import com.example.reservafutbol.Servicio.S3StorageService;
import com.example.reservafutbol.Repositorio.RoleRepositorio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
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
    private S3StorageService s3StorageService;

    @Autowired
    private RoleRepositorio roleRepositorio;

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

        // Lógica para intentar dividir nombreCompleto en nombre y apellido para el DTO
        String nombre = "";
        String apellido = "";
        if (user.getNombreCompleto() != null && !user.getNombreCompleto().isBlank()) {
            // Divide por el primer espacio y toma la primera parte como nombre, el resto como apellido
            String[] partesNombre = user.getNombreCompleto().trim().split("\\s+", 2);
            nombre = partesNombre[0];
            if (partesNombre.length > 1) {
                apellido = partesNombre[1];
            }
        }

        PerfilDTO perfilDTO = new PerfilDTO(
                user.getNombreCompleto(), // Campo existente, si aún se usa en frontend
                nombre,                   // Campo 'nombre' para precarga en formularios
                apellido,                 // Campo 'apellido' para precarga en formularios
                user.getUbicacion(),
                user.getEdad(),
                user.getBio(),
                user.getUsername(),       // Esto es el email del usuario
                user.getProfilePictureUrl(),
                roles,
                user.getTelefono()        // Incluir el teléfono
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
        // Asegúrate de que tu servicio `updateUserProfile` puede manejar el teléfono
        // Si tu modelo `User` tiene campos `nombre` y `apellido` separados, y el `PerfilDTO` los envía,
        // deberías pasar `perfilDTO.getNombre()` y `perfilDTO.getApellido()` aquí.
        // Por ahora, `nombreCompleto` es lo que se actualiza.
        usuarioServicio.updateUserProfile(
                user,
                perfilDTO.getNombreCompleto(), // Se asume que el frontend envía el nombreCompleto actualizado
                perfilDTO.getUbicacion(),
                perfilDTO.getEdad(),
                perfilDTO.getBio(),
                perfilDTO.getTelefono() // Pasar el teléfono al servicio
        );

        return ResponseEntity.ok("Perfil actualizado correctamente.");
    }

    @PostMapping("/me/profile-picture")
    public ResponseEntity<?> subirFotoPerfil(@RequestParam("file") MultipartFile file, Authentication auth) {
        Optional<User> optUser = obtenerUsuarioAutenticado(auth);
        if (optUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Debe iniciar sesión.");
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("No se ha seleccionado ningún archivo.");
        }

        if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            return ResponseEntity.badRequest().body("El archivo debe ser una imagen válida.");
        }

        try {
            String fileUrl = s3StorageService.uploadFile(file);
            usuarioServicio.updateProfilePictureUrl(optUser.get(), fileUrl);

            return ResponseEntity.ok(Map.of("profilePictureUrl", fileUrl));
        } catch (IOException e) {
            log.error("Error al subir la imagen de perfil a S3: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al subir la imagen.");
        }
    }

    // Nuevo endpoint: Listar todos los usuarios (Solo ADMIN)
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<User>> getAllUsers() {
        log.info("GET /api/users - Obteniendo todos los usuarios (solo ADMIN).");
        List<User> users = usuarioServicio.findAllEnabledUsers();
        return ResponseEntity.ok(users);
    }

    // Nuevo endpoint: Asignar/Quitar roles a un usuario por ADMIN
    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUserRoles(@PathVariable Long userId, @RequestBody List<String> newRoles, Authentication authentication) {
        String adminUsername = authentication.getName();
        log.info("PUT /api/users/{}/roles - Admin {} intentando actualizar roles para usuario {}", userId, adminUsername, userId);

        try {
            Optional<User> userOptional = usuarioServicio.findById(userId);
            if (userOptional.isEmpty()) {
                log.warn("Intento de actualizar roles para usuario no encontrado: {}", userId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado.");
            }
            User user = userOptional.get();

            Set<Role> rolesToAssign = new HashSet<>();
            for (String roleName : newRoles) {
                ERole eRole;
                try {
                    eRole = ERole.valueOf(roleName);
                } catch (IllegalArgumentException e) {
                    log.warn("Rol inválido recibido: {}", roleName);
                    return ResponseEntity.badRequest().body("Rol inválido: " + roleName);
                }
                Role role = roleRepositorio.findByName(eRole)
                        .orElseThrow(() -> new RuntimeException("Error: Rol " + roleName + " no encontrado en la BD."));
                rolesToAssign.add(role);
            }

            User currentAdmin = (User)authentication.getPrincipal();
            // Asegúrate de que el ADMIN no se quite a sí mismo el rol de ADMIN si no tiene otros ADMINs
            // Esta lógica puede ser más compleja, pero por ahora, una simple verificación de IDs
            if (user.getId().equals(currentAdmin.getId()) && !rolesToAssign.stream().anyMatch(r -> r.getName().equals(ERole.ROLE_ADMIN))) {
                // Puedes implementar una lógica más robusta si quieres, por ejemplo,
                // si hay más de un ADMIN, permitirle quitarse el rol.
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("No puedes quitarte el rol ADMIN a ti mismo.");
            }

            user.setRoles(rolesToAssign);
            usuarioServicio.save(user);

            log.info("Roles de usuario {} actualizados a: {}", userId, newRoles);
            return ResponseEntity.ok("Roles actualizados correctamente para el usuario " + user.getUsername() + ".");

        } catch (Exception e) {
            log.error("Error al actualizar roles para usuario {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al actualizar roles: " + e.getMessage());
        }
    }

    private Optional<User> obtenerUsuarioAutenticado(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        return usuarioServicio.findByUsername(auth.getName());
    }
}