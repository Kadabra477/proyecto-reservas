package com.example.reservafutbol.Controlador;

import com.example.reservafutbol.Configuracion.JWTUtil;
import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Servicio.EmailService;
import com.example.reservafutbol.Servicio.UsuarioServicio;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

record ForgotPasswordRequest(@NotBlank @Email String email) {}
// Ejemplo DTO para Reset Password Request
record ResetPasswordRequest(@NotBlank String token,
                            @NotBlank @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres") String newPassword) {}



@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Value("${frontend.url}")
    private String frontendUrl;

    @Autowired
    private UsuarioServicio usuarioServicio;

    @Autowired
    private JWTUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody User nuevoUsuario) {
        // Validaciones básicas (puedes añadir más)
        if (nuevoUsuario.getNombreCompleto() == null || nuevoUsuario.getNombreCompleto().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("❌ El nombre completo es obligatorio.");
        }
        if (nuevoUsuario.getUsername() == null || nuevoUsuario.getUsername().trim().isEmpty() || !nuevoUsuario.getUsername().contains("@")) {
            return ResponseEntity.badRequest().body("❌ El correo electrónico (username) es inválido.");
        }
        if (nuevoUsuario.getPassword() == null || nuevoUsuario.getPassword().length() < 6) { // Asumiendo min 6 caracteres
            return ResponseEntity.badRequest().body("❌ La contraseña debe tener al menos 6 caracteres.");
        }

        // Verificar si ya existe
        if (usuarioServicio.findByUsername(nuevoUsuario.getUsername()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("❌ El correo electrónico ya está registrado.");
        }

        // Preparar usuario
        nuevoUsuario.setPassword(passwordEncoder.encode(nuevoUsuario.getPassword()));
        nuevoUsuario.setRol("USER");
        nuevoUsuario.setActive(false); // Inicia inactivo
        String token = UUID.randomUUID().toString();
        nuevoUsuario.setValidationToken(token);

        // Intentar guardar y enviar email
        try {
            User usuarioGuardado = usuarioServicio.guardarUsuarioConRetorno(nuevoUsuario);
            emailService.sendValidationEmail(usuarioGuardado.getUsername(), token);
            return ResponseEntity.ok("✅ Registro casi completo. Revisa tu correo electrónico ("+ usuarioGuardado.getUsername() +") para validar la cuenta.");
        } catch (Exception e) {
            System.err.println("Error en /register: " + e.getMessage());
            // Podrías intentar borrar el usuario si el email falló, o manejarlo de otra forma
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("❌ Ocurrió un error interno durante el registro.");
        }
    }

    // --- NUEVO ENDPOINT DE VALIDACIÓN ---
    @GetMapping("/validate")
    public ResponseEntity<String> validateAccount(@RequestParam("token") String token) {
        if (token == null || token.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Token inválido.");
        }
        boolean isValidated = usuarioServicio.validateUser(token);
        if (isValidated) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", frontendUrl + "/login?validated=true")
                    .build();

        } else {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", frontendUrl + "/login?error=validation_failed") // Ajusta la URL del frontend
                    .build();
        }
    }
    // --- FIN ENDPOINT ---

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User userRequest) {
        Optional<User> usuarioOpt = usuarioServicio.findByUsername(userRequest.getUsername());

        if (usuarioOpt.isPresent()) {
            User usuario = usuarioOpt.get();

            // VERIFICACIÓN DE CUENTA ACTIVA (Ya implementada en usuarioServicio -> loadUserByUsername con LockedException)
            // El manejo de LockedException debería ocurrir en un AuthenticationFailureHandler o ser capturado
            // Alternativamente, puedes verificar aquí ANTES de intentar autenticar:
            if (!usuario.getActive()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Tu cuenta no está activa. Por favor, valida tu correo electrónico."));
            }

            // Comparar contraseña
            if (passwordEncoder.matches(userRequest.getPassword(), usuario.getPassword())) {
                // Generar token y devolver datos
                String token = jwtUtil.generateToken(usuario.getUsername(), usuario.getRol());
                Map<String, String> response = Map.of(
                        "token", token,
                        "username", usuario.getUsername(),
                        "nombreCompleto", usuario.getNombreCompleto() != null ? usuario.getNombreCompleto() : ""
                );
                return ResponseEntity.ok(response);
            }
        }
        // Si no encontró usuario o la contraseña no coincide
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Credenciales incorrectas"));
    }
    // --- NUEVO: Endpoint para Solicitar Reseteo ---
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        // Llama al servicio para crear el token
        String token = usuarioServicio.createPasswordResetToken(request.email());

        // Si se generó un token (implica que el usuario existe y no hubo error)
        if (token != null) {
            try {
                // Llama al servicio para enviar el email
                emailService.sendPasswordResetEmail(request.email(), token);
                // Devuelve un mensaje genérico para no revelar si el email existe
                return ResponseEntity.ok(Map.of("message", "Si la dirección de correo está registrada, recibirás un enlace para restablecer tu contraseña."));
            } catch (Exception e) {
                System.err.println("Error al ENVIAR email de reseteo para " + request.email() + ": " + e.getMessage());
                // Error interno aunque el token se haya creado
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Error al intentar enviar el correo de restablecimiento."));
            }
        } else {
            // Si el servicio no generó token (usuario no encontrado), devuelve el mismo mensaje genérico
            System.out.println("Solicitud de reseteo para email no registrado: " + request.email());
            return ResponseEntity.ok(Map.of("message", "Si la dirección de correo está registrada, recibirás un enlace para restablecer tu contraseña."));
        }
    }
    // --- FIN Endpoint Forgot Password ---


    // --- NUEVO: Endpoint para Ejecutar el Reseteo ---
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody ResetPasswordRequest request) {
        boolean success = usuarioServicio.resetPassword(request.token(), request.newPassword());

        if (success) {
            return ResponseEntity.ok(Map.of("message", "¡Contraseña actualizada con éxito!"));
        } else {
            // El error puede ser token inválido, expirado o contraseña inválida
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No se pudo restablecer la contraseña. El enlace puede ser inválido, haber expirado o la nueva contraseña no cumple los requisitos."));
        }
    }
    // --- FIN Endpoint Reset Password ---

}