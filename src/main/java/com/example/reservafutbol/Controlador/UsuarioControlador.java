package com.example.reservafutbol.Controlador;

import com.example.reservafutbol.DTO.PerfilDTO; // Cambiado de PerfilDTO a UserProfileDTO
import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Servicio.UsuarioServicio; // Usar el servicio de usuario
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

// Clase de ejemplo para el servicio de almacenamiento de imágenes
// En un proyecto real, esto sería una interfaz y una implementación separadas
// que se conectaría a AWS S3, Cloudinary, etc.
class ImageStorageService {
    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);

    // Método dummy para simular el almacenamiento y devolver una URL
    public String storeFile(MultipartFile file) throws IOException {
        // En una aplicación real, aquí subirías el archivo a S3, Cloudinary, etc.
        // y devolverías la URL pública del archivo.
        // Por ahora, solo simula una URL.
        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        String dummyUrl = "https://tu-bucket-s3.s3.amazonaws.com/perfiles/" + fileName;
        log.info("Simulando almacenamiento de imagen: {} -> {}", file.getOriginalFilename(), dummyUrl);
        // Aquí podrías guardar el archivo en un directorio temporal para pruebas locales
        // Path filePath = Paths.get("uploads").toAbsolutePath().normalize().resolve(fileName);
        // Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        return dummyUrl;
    }
}


@RestController
@RequestMapping("/api/users") // Cambiado el path de la clase
public class UsuarioControlador { // Cambiado el nombre de la clase

    private static final Logger log = LoggerFactory.getLogger(UsuarioControlador.class);

    @Autowired
    private UsuarioServicio usuarioServicio; // Inyectar el servicio de usuario

    // Deberías inyectar un servicio real de almacenamiento de imágenes
    private final ImageStorageService imageStorageService = new ImageStorageService(); // Dummy service

    // Endpoint para obtener el perfil del usuario logueado
    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Debe iniciar sesión.");
        }

        String email = auth.getName(); // El nombre de autenticación es el email (username)
        Optional<User> optUser = usuarioServicio.findByUsername(email);

        if (optUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado.");
        }

        User user = optUser.get();
        // Mapear la entidad User a un DTO para enviar solo los datos necesarios al frontend
        PerfilDTO userProfileDTO = new PerfilDTO(
                user.getNombreCompleto(),
                user.getUbicacion(),
                user.getEdad(),
                user.getBio(), // Incluir bio
                user.getUsername(), // Email
                user.getProfilePictureUrl() // Incluir URL de la foto de perfil
        );

        return ResponseEntity.ok(userProfileDTO);
    }

    // Endpoint para actualizar el perfil del usuario
    // Usamos PUT porque el frontend envía todos los campos, aunque PatchMapping también sería válido.
    @PutMapping("/me")
    public ResponseEntity<?> updateProfile(@RequestBody PerfilDTO perfilDTO, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Debe iniciar sesión.");
        }

        String email = auth.getName();
        Optional<User> optUser = usuarioServicio.findByUsername(email);
        if (optUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado.");
        }

        User u = optUser.get();
        // Usamos el servicio para actualizar los campos
        usuarioServicio.updateUserProfile(
                u,
                perfilDTO.getNombreCompleto(),
                perfilDTO.getUbicacion(),
                perfilDTO.getEdad(),
                perfilDTO.getBio()
        );

        return ResponseEntity.ok("Perfil actualizado correctamente.");
    }

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
            // Aquí iría la lógica real para subir la imagen a un servicio de almacenamiento (S3, Cloudinary, etc.)
            // y obtener la URL pública.
            String fileUrl = imageStorageService.storeFile(file); // Usando el dummy service

            User user = optUser.get();
            usuarioServicio.updateProfilePictureUrl(user, fileUrl); // Actualizar la URL en la BD

            // Devolver la URL de la imagen al frontend
            Map<String, String> response = new HashMap<>();
            response.put("profilePictureUrl", fileUrl);
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Error al subir la imagen de perfil: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al subir la imagen.");
        }
    }
}