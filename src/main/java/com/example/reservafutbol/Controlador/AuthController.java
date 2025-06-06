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
import java.util.List;
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

import java.util.HashSet;
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
    UsuarioServicio usuarioServicio; // Inyectar UsuarioServicio

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
        log.info("POST /api/auth/login - Intento de login para: {}", loginRequest.getUsername());
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtUtil.generateJwtToken(authentication);

            User userDetails = (User) authentication.getPrincipal(); // Obtener el objeto User real

            // Verificar si la cuenta está habilitada
            if (!userDetails.isEnabled()) { // Usar .isEnabled() del modelo User
                log.warn("Login fallido para {}: cuenta no activada.", userDetails.getUsername());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Error: Cuenta no activada. Por favor, verifica tu email.");
            }

            // Obtener los roles del usuario para el frontend
            List<String> roles = userDetails.getAuthorities().stream()
                    .map(item -> item.getAuthority())
                    .collect(Collectors.toList());

            // Obtener el rol principal (asumiendo uno por simplicidad para el frontend)
            String mainRole = roles.isEmpty() ? "USER" : roles.get(0).replace("ROLE_", "");

            log.info("Login exitoso para {}. Rol: {}", userDetails.getUsername(), mainRole);

            return ResponseEntity.ok(new JwtResponse(jwt,
                    userDetails.getId(),
                    userDetails.getUsername(),
                    userDetails.getEmail(),
                    userDetails.getNombreCompleto(),
                    mainRole)); // Enviar el rol principal
        } catch (Exception e) {
            log.error("Error durante el login para {}: {}", loginRequest.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Error: Usuario o contraseña incorrectos, o cuenta no activada.");
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest signUpRequest) {
        log.info("POST /api/auth/register - Intento de registro para email: {}", signUpRequest.getEmail());

        if (usuarioRepositorio.existsByUsername(signUpRequest.getUsername())) {
            log.warn("Registro fallido: Username '{}' ya está en uso.", signUpRequest.getUsername());
            return ResponseEntity
                    .badRequest()
                    .body("Error: ¡El nombre de usuario ya está en uso!");
        }

        if (usuarioRepositorio.existsByEmail(signUpRequest.getEmail())) {
            log.warn("Registro fallido: Email '{}' ya está en uso.", signUpRequest.getEmail());
            return ResponseEntity
                    .badRequest()
                    .body("Error: ¡El correo electrónico ya está en uso!");
        }

        // Crear nuevo usuario
        User nuevoUsuario = new User(
                signUpRequest.getUsername(),
                signUpRequest.getEmail(),
                encoder.encode(signUpRequest.getPassword()),
                signUpRequest.getNombreCompleto());

        // Asignar roles (por defecto ROLE_USER)
        Set<Role> roles = new HashSet<>();
        Role userRole = roleRepositorio.findByName(ERole.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("Error: Rol de usuario no encontrado."));
        roles.add(userRole);
        nuevoUsuario.setRoles(roles); // Usar setRoles()

        // El resto de campos del perfil se inicializan o se pueden establecer por defecto
        nuevoUsuario.setUbicacion(signUpRequest.getUbicacion());
        // CompletoPerfil es un booleano, se inicializa en el constructor de User o en el servicio
        nuevoUsuario.setCompletoPerfil(false); // Por defecto al registrar
        // Estos campos ahora son parte del modelo User
        // nuevoUsuario.setActive(false); // Se establece en el servicio registerNewUser()
        // nuevoUsuario.setValidationToken(UUID.randomUUID().toString()); // Se establece en el servicio registerNewUser()

        try {
            // Usa el servicio de usuario para manejar la creación y envío de email de validación
            usuarioServicio.registerNewUser(nuevoUsuario);
            log.info("Usuario '{}' registrado exitosamente. Email de validación enviado.", signUpRequest.getUsername());
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
            usuarioServicio.createPasswordResetTokenForUser(email);
            log.info("Link de reseteo de contraseña enviado a: {}", email);
            return ResponseEntity.ok("Si tu email está registrado, recibirás un enlace para restablecer tu contraseña.");
        } catch (UsernameNotFoundException e) {
            log.warn("Intento de reseteo de contraseña para email no registrado: {}", email);
            // No revelamos si el email existe por seguridad
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
        log.info("Contraseña restablecida exitosamente para usuario: {}", usuario.getUsername());
        return ResponseEntity.ok("Contraseña restablecida exitosamente.");
    }

    // Endpoint para validar un token JWT (útil para el frontend al cargar la app)
    @GetMapping("/validate-token")
    public ResponseEntity<String> validateJwtToken(@RequestHeader("Authorization") String authorizationHeader) {
        log.info("GET /api/auth/validate-token - Validando token JWT.");
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            log.warn("Intento de validación de token sin cabecera Authorization válida.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token no proporcionado.");
        }
        String token = authorizationHeader.substring(7); // Quitar "Bearer "

        if (jwtUtil.validateJwtToken(token)) { // Usar validateJwtToken del JWTUtil
            log.info("Token JWT válido.");
            return ResponseEntity.ok("Token JWT válido.");
        } else {
            log.warn("Token JWT inválido o expirado.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token inválido o expirado.");
        }
    }
}