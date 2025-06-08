package com.example.reservafutbol.Controlador;

import com.example.reservafutbol.DTO.PerfilDTO;
import com.example.reservafutbol.Modelo.ERole;
import com.example.reservafutbol.Modelo.Role;
import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Servicio.UsuarioServicio;
import com.example.reservafutbol.Servicio.S3StorageService;
import com.example.reservafutbol.Repositorio.RoleRepositorio; // Importar RoleRepositorio
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
import java.util.HashSet; // Importar HashSet
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

    // Obtener el perfil del usuario autenticado
    @GetMapping("/me")
    public ResponseEntity<?> obtenerPerfil(Authentication auth) {
        Optional<User> optUser = obtenerUsuarioAutenticado(auth);
        if (optUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Debe iniciar sesión.");
        }

        User user = optUser.get();
        // Incluye los roles en el DTO del perfil
        List<String> roles = user.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());

        PerfilDTO perfilDTO = new PerfilDTO(
                user.getNombreCompleto(),
                user.getUbicacion(),
                user.getEdad(),
                user.getBio(),
                user.getUsername(), // El email es el username
                user.getProfilePictureUrl(),
                roles // Pasa los roles en el DTO (CORREGIDO)
        );

        return ResponseEntity.ok(perfilDTO);
    }

    // Actualizar el perfil del usuario autenticado
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

    // Subir la foto de perfil a S3
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
        List<User> users = usuarioServicio.findAllUsers(); // Usar un método del servicio para listar todos
        return ResponseEntity.ok(users);
    }

    // Nuevo endpoint: Asignar/Quitar roles a un usuario por ADMIN
    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasRole('ADMIN')") // Solo el ADMIN puede modificar roles
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

            // Lógica para construir el Set<Role> a partir de List<String>
            Set<Role> rolesToAssign = new HashSet<>(); // Importar HashSet
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

            // Lógica de seguridad para no permitir que un ADMIN se quite el rol ADMIN a sí mismo o a otros ADMINS fácilmente
            User currentAdmin = (User)authentication.getPrincipal();
            if (user.getId().equals(currentAdmin.getId()) && !rolesToAssign.contains(roleRepositorio.findByName(ERole.ROLE_ADMIN).get())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("No puedes quitarte el rol ADMIN a ti mismo.");
            }

            user.setRoles(rolesToAssign);
            usuarioServicio.save(user); // Guarda el usuario con los nuevos roles

            log.info("Roles de usuario {} actualizados a: {}", userId, newRoles);
            return ResponseEntity.ok("Roles actualizados correctamente para el usuario " + user.getUsername() + ".");

        } catch (Exception e) {
            log.error("Error al actualizar roles para usuario {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al actualizar roles: " + e.getMessage());
        }
    }

    // Método auxiliar privado para centralizar la obtención del usuario autenticado
    private Optional<User> obtenerUsuarioAutenticado(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        return usuarioServicio.findByUsername(auth.getName());
    }
}