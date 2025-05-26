package com.example.reservafutbol.Controlador;

import com.example.reservafutbol.DTO.PerfilDTO;
import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Servicio.UsuarioServicio;
import com.example.reservafutbol.Servicio.S3StorageService; // ¡Importa tu nuevo servicio S3!
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


@RestController
@RequestMapping("/api/users")
public class UsuarioControlador {

    private static final Logger log = LoggerFactory.getLogger(UsuarioControlador.class);

    @Autowired
    private UsuarioServicio usuarioServicio;

    @Autowired // ¡Inyecta tu servicio real de S3!
    private S3StorageService s3StorageService;

    // Elimina o comenta tu clase ImageStorageService dummy si la tenías dentro de este archivo.
    // O elimina la línea: private final ImageStorageService imageStorageService = new ImageStorageService();

    // ... (Mantén tus métodos getMyProfile y updateProfile sin cambios, a menos que el DTO sea diferente)

    // Endpoint para subir la foto de perfil
    @PostMapping("/me/profile-picture")
    public ResponseEntity<?> uploadProfilePicture(@RequestParam("file") MultipartFile file, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Debe iniciar sesión.");
        }

        String email = auth.getName();
        Optional<User> optUser = usuarioServicio.findByUsername(email);
        if (optUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado.");
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("No se ha seleccionado ningún archivo.");
        }
        if (!file.getContentType().startsWith("image/")) {
            return ResponseEntity.badRequest().body("El archivo debe ser una imagen.");
        }
        // Puedes añadir más validaciones de tamaño, etc.

        try {
            // ¡Aquí es donde usas tu servicio real de S3 para subir la imagen!
            String fileUrl = s3StorageService.uploadFile(file);

            User user = optUser.get();
            usuarioServicio.updateProfilePictureUrl(user, fileUrl); // Actualiza la URL en la BD

            // Devolver la URL de la imagen al frontend
            Map<String, String> response = new HashMap<>();
            response.put("profilePictureUrl", fileUrl);
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Error al subir la imagen de perfil a S3: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al subir la imagen.");
        }
    }
}