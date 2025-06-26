package com.example.reservafutbol.Controlador;

import com.example.reservafutbol.DTO.PerfilDTO;
import com.example.reservafutbol.Modelo.ERole;
import com.example.reservafutbol.Modelo.Role;
import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Servicio.UsuarioServicio;
import com.example.reservafutbol.Servicio.S3StorageService;
import com.example.reservafutbol.Repositorio.RoleRepositorio; // Mantenemos esta si es necesaria para otras partes
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
                user.getProfilePictureUrl(),
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
                // El frontend envía nombre y apellido por separado, asumo que se reconstruye el nombreCompleto en el DTO o aquí.
                // Si el DTO ya tiene nombreCompleto, usa perfilDTO.getNombreCompleto()
                // Si el DTO solo tiene nombre y apellido, deberías combinarlos aquí o en el DTO.
                // Por simplicidad, asumo que 'nombreCompleto' viene bien en el DTO para el servicio.
                perfilDTO.getNombreCompleto(),
                perfilDTO.getUbicacion(),
                perfilDTO.getEdad(),
                perfilDTO.getBio()
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

    // LISTAR TODOS LOS USUARIOS (Solo ADMIN) - Este está bien y es necesario
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<User>> getAllUsers() {
        log.info("GET /api/users - Obteniendo todos los usuarios (solo ADMIN).");
        List<User> users = usuarioServicio.findAllUsers();
        return ResponseEntity.ok(users);
    }

    private Optional<User> obtenerUsuarioAutenticado(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        return usuarioServicio.findByUsername(auth.getName());
    }
}