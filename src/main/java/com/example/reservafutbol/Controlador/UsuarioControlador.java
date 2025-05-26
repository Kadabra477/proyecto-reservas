package com.example.reservafutbol.Controlador;

import com.example.reservafutbol.DTO.PerfilDTO;
import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Servicio.UsuarioServicio;
import com.example.reservafutbol.Servicio.S3StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class UsuarioControlador {

    private static final Logger log = LoggerFactory.getLogger(UsuarioControlador.class);

    @Autowired
    private UsuarioServicio usuarioServicio;

    @Autowired
    private S3StorageService s3StorageService;

    // Obtener el perfil del usuario autenticado
    @GetMapping("/me")
    public ResponseEntity<?> obtenerPerfil(Authentication auth) {
        Optional<User> optUser = obtenerUsuarioAutenticado(auth);
        if (optUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Debe iniciar sesión.");
        }

        User user = optUser.get();
        PerfilDTO perfilDTO = new PerfilDTO(
                user.getNombreCompleto(),
                user.getUbicacion(),
                user.getEdad(),
                user.getBio(),
                user.getUsername(),
                user.getProfilePictureUrl()
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

    // Método auxiliar privado para centralizar la obtención del usuario autenticado
    private Optional<User> obtenerUsuarioAutenticado(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        return usuarioServicio.findByUsername(auth.getName());
    }
}
