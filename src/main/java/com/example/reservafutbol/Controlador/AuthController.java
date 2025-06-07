package com.example.reservafutbol.Controlador;

import com.example.reservafutbol.Configuracion.JWTUtil;
import com.example.reservafutbol.Modelo.ERole;
import com.example.reservafutbol.Modelo.Role;
import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Repositorio.RoleRepositorio;
import com.example.reservafutbol.Repositorio.UsuarioRepositorio;
import com.example.reservafutbol.Servicio.EmailService;
import com.example.reservafutbol.Servicio.UsuarioServicio;
import com.example.reservafutbol.payload.request.LoginRequest;
import com.example.reservafutbol.payload.request.PasswordResetRequest;
import com.example.reservafutbol.payload.request.RegisterRequest;
import com.example.reservafutbol.payload.response.JwtResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UsuarioRepositorio usuarioRepositorio;

    @Autowired
    RoleRepositorio roleRepositorio;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JWTUtil jwtUtil;

    @Autowired
    EmailService emailService;

    @Autowired
    UsuarioServicio usuarioServicio;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
        log.info("POST /api/auth/login - Intento de login para: {}", loginRequest.getUsername());
        try {
            // El username que viene del frontend es el email para login
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtUtil.generateJwtToken(authentication);

            User userDetails = (User) authentication.getPrincipal();

            if (!userDetails.isEnabled()) {
                log.warn("Login fallido para {}: cuenta no activada.", userDetails.getUsername());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Error: Cuenta no activada. Por favor, verifica tu email.");
            }

            List<String> roles = userDetails.getAuthorities().stream()
                    .map(item -> item.getAuthority())
                    .collect(Collectors.toList());

            String mainRole = roles.isEmpty() ? "USER" : roles.get(0).replace("ROLE_", "");

            log.info("Login exitoso para {}. Rol: {}", userDetails.getUsername(), mainRole);

            // Constructor de JwtResponse adaptado: username es el email, nombreCompleto es campo aparte
            return ResponseEntity.ok(new JwtResponse(jwt,
                    userDetails.getId(),
                    userDetails.getUsername(), // username es el email para login
                    userDetails.getNombreCompleto(), // nombreCompleto es el campo de perfil
                    mainRole));
        } catch (Exception e) {
            log.error("Error durante el login para {}: {}", loginRequest.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Error: Usuario o contraseña incorrectos, o cuenta no activada.");
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest signUpRequest) {
        log.info("POST /api/auth/register - Intento de registro para email: {}", signUpRequest.getEmail());

        // Verificamos si el email ya existe como username
        if (usuarioRepositorio.existsByUsername(signUpRequest.getEmail())) { // Buscar por email (que es el username)
            log.warn("Registro fallido: Email '{}' ya está en uso como nombre de usuario.", signUpRequest.getEmail());
            return ResponseEntity
                    .badRequest()
                    .body("Error: ¡El correo electrónico ya está en uso!");
        }

        // Crear nuevo usuario (el email del RegisterRequest se usará como username)
        User nuevoUsuario = new User(
                signUpRequest.getEmail(), // Email del RegisterRequest se asigna a username del User
                encoder.encode(signUpRequest.getPassword()),
                signUpRequest.getNombreCompleto()); // nombreCompleto del RegisterRequest se asigna a nombreCompleto del User

        Set<Role> roles = new HashSet<>();
        Role userRole = roleRepositorio.findByName(ERole.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("Error: Rol de usuario no encontrado."));
        roles.add(userRole);
        nuevoUsuario.setRoles(roles);

        // Asignar otros campos de perfil desde RegisterRequest (si el modelo User los tiene)
        nuevoUsuario.setUbicacion(signUpRequest.getUbicacion());
        nuevoUsuario.setEdad(signUpRequest.getEdad());
        nuevoUsuario.setTelefono(signUpRequest.getTelefono());
        nuevoUsuario.setBio(signUpRequest.getBio());

        try {
            usuarioServicio.registerNewUser(nuevoUsuario);
            log.info("Usuario '{}' registrado exitosamente. Email de validación enviado.", signUpRequest.getEmail());
            return ResponseEntity.ok("Usuario registrado exitosamente. Por favor, revisa tu email para activar tu cuenta.");
        } catch (Exception e) {
            log.error("Error durante el registro de usuario: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: No se pudo registrar el usuario. Intenta de nuevo.");
        }
    }

    @GetMapping("/validate")
    public ResponseEntity<String> validateAccount(@RequestParam("token") String token) {
        log.info("GET /api/auth/validate - Intentando validar cuenta con token: {}", token);
        boolean activated = usuarioServicio.activateUser(token);
        if (activated) {
            log.info("Cuenta activada exitosamente para token: {}", token);
            return ResponseEntity.ok("¡Tu cuenta ha sido activada exitosamente! Ya puedes iniciar sesión.");
        } else {
            log.warn("Fallo en la activación de cuenta: token inválido o expirado: {}", token);
            return ResponseEntity.badRequest().body("Error: Token de activación inválido o expirado.");
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestParam("email") String email) {
        log.info("POST /api/auth/forgot-password - Solicitud de reseteo de contraseña para email: {}", email);
        try {
            usuarioServicio.createPasswordResetTokenForUser(email); // email es el username
            log.info("Link de reseteo de contraseña enviado a: {}", email);
            return ResponseEntity.ok("Si tu email está registrado, recibirás un enlace para restablecer tu contraseña.");
        } catch (UsernameNotFoundException e) {
            log.warn("Intento de reseteo de contraseña para email no registrado: {}", email);
            return ResponseEntity.ok("Si tu email está registrado, recibirás un enlace para restablecer tu contraseña.");
        } catch (Exception e) {
            log.error("Error al solicitar reseteo de contraseña para {}: {}", email, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al procesar la solicitud de reseteo de contraseña.");
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody PasswordResetRequest request) {
        log.info("POST /api/auth/reset-password - Intentando restablecer contraseña con token.");
        Optional<User> userOptional = usuarioServicio.validatePasswordResetToken(request.getToken());
        if (userOptional.isEmpty()) {
            log.warn("Intento de reseteo de contraseña con token inválido o expirado.");
            return ResponseEntity.badRequest().body("Token de restablecimiento de contraseña inválido o expirado.");
        }
        User usuario = userOptional.get();
        usuarioServicio.updatePassword(usuario, encoder.encode(request.getNewPassword()));
        // CORRECCIÓN AQUÍ: Usar getUsername() directamente del objeto 'usuario'
        log.info("Contraseña restablecida exitosamente para usuario: {}", usuario.getUsername());
        return ResponseEntity.ok("Contraseña restablecida exitosamente.");
    }

    @GetMapping("/validate-token")
    public ResponseEntity<String> validateJwtToken(@RequestHeader("Authorization") String authorizationHeader) {
        log.info("GET /api/auth/validate-token - Validando token JWT.");
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            log.warn("Intento de validación de token sin cabecera Authorization válida.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token no proporcionado.");
        }
        String token = authorizationHeader.substring(7);

        if (jwtUtil.validateJwtToken(token)) {
            log.info("Token JWT válido.");
            return ResponseEntity.ok("Token JWT válido.");
        } else {
            log.warn("Token JWT inválido o expirado.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token inválido o expirado.");
        }
    }
}