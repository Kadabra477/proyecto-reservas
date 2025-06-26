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
import org.springframework.security.access.prepost.PreAuthorize;
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
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtUtil.generateJwtToken(authentication);

            User userDetails = (User) authentication.getPrincipal();

            if (!userDetails.isEnabled()) {
                log.warn("Login fallido para {}: cuenta no activada.", userDetails.getUsername());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Error: Cuenta no activada. Por favor, contacta a un administrador.");
            }

            List<String> roles = userDetails.getAuthorities().stream()
                    .map(item -> item.getAuthority())
                    .collect(Collectors.toList());

            // Si necesitas pasar un solo "rol principal" al frontend, tu lógica aquí es correcta.
            // Sin embargo, en el frontend ya estamos usando un array de roles completo,
            // por lo que este "mainRole" podría ser menos relevante si el frontend ya itera sobre el array.
            String mainRole = "USER"; // Default
            if (roles.contains(ERole.ROLE_ADMIN.name())) {
                mainRole = ERole.ROLE_ADMIN.name().replace("ROLE_", "");
            } else if (roles.contains(ERole.ROLE_COMPLEX_OWNER.name())) {
                mainRole = ERole.ROLE_COMPLEX_OWNER.name().replace("ROLE_", "");
            } else if (roles.contains(ERole.ROLE_USER.name())) { // Asegura que 'USER' sea el default si no hay otros
                mainRole = ERole.ROLE_USER.name().replace("ROLE_", "");
            }

            log.info("Login exitoso para {}. Rol principal: {}. Todos los roles: {}", userDetails.getUsername(), mainRole, roles);

            // Se devuelve el mainRole como String. Si el frontend espera un array de roles,
            // este DTO debería ser actualizado para devolver 'roles' (List<String>) en lugar de 'mainRole' (String).
            // Ya que el frontend parece manejar un array, no se hace un cambio aquí, pero es algo a considerar si hay un desajuste.
            return ResponseEntity.ok(new JwtResponse(jwt,
                    userDetails.getId(),
                    userDetails.getUsername(),
                    userDetails.getNombreCompleto(),
                    mainRole)); // O deberías devolver 'roles' aquí si el DTO lo soporta.
        } catch (org.springframework.security.core.AuthenticationException e) {
            log.error("Error de autenticación durante el login para {}: {}", loginRequest.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Error: Usuario o contraseña incorrectos, o cuenta no activada.");
        } catch (Exception e) {
            log.error("Error inesperado durante el login para {}: {}", loginRequest.getUsername(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error interno del servidor al intentar iniciar sesión.");
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest signUpRequest) {
        log.info("POST /api/auth/register - Intento de registro para email: {}", signUpRequest.getEmail());

        if (usuarioRepositorio.existsByUsername(signUpRequest.getEmail())) {
            log.warn("Registro fallido: Email '{}' ya está en uso como nombre de usuario.", signUpRequest.getEmail());
            return ResponseEntity
                    .badRequest()
                    .body("Error: ¡El correo electrónico ya está en uso!");
        }

        User nuevoUsuario = new User(
                signUpRequest.getEmail(),
                encoder.encode(signUpRequest.getPassword()),
                signUpRequest.getNombreCompleto());

        Set<Role> roles = new HashSet<>();
        Role userRole = roleRepositorio.findByName(ERole.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("Error: Rol de usuario no encontrado."));
        roles.add(userRole);
        nuevoUsuario.setRoles(roles);
        try {
            usuarioServicio.registerNewUser(nuevoUsuario);
            log.info("Usuario '{}' registrado exitosamente y pendiente de activación admin.", signUpRequest.getEmail());
            return ResponseEntity.ok("Usuario registrado exitosamente. Tu cuenta está en proceso de activación. Un administrador la habilitará en breve.");
        } catch (Exception e) {
            log.error("Error durante el registro de usuario {}: {}", signUpRequest.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: No se pudo registrar el usuario. Intenta de nuevo más tarde.");
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
            // Esto es intencional para no revelar si el email existe o no por seguridad.
            return ResponseEntity.ok("Si tu email está registrado, recibirás un enlace para restablecer tu contraseña.");
        } catch (Exception e) {
            log.error("Error al solicitar reseteo de contraseña para {}: {}", email, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al procesar la solicitud de reseteo de contraseña.");
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody PasswordResetRequest request) {
        log.info("POST /api/auth/reset-password - Intentando restablecer contraseña con token.");
        try {
            Optional<User> userOptional = usuarioServicio.validatePasswordResetToken(request.getToken());
            if (userOptional.isEmpty()) {
                log.warn("Intento de reseteo de contraseña con token inválido o expirado.");
                return ResponseEntity.badRequest().body("Token de restablecimiento de contraseña inválido o expirado.");
            }
            User usuario = userOptional.get();
            usuarioServicio.updatePassword(usuario, encoder.encode(request.getNewPassword()));
            log.info("Contraseña restablecida exitosamente para usuario: {}", usuario.getUsername());
            return ResponseEntity.ok("Contraseña restablecida exitosamente.");
        } catch (Exception e) {
            log.error("Error al restablecer contraseña: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error interno al restablecer la contraseña.");
        }
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

    // DTO para la solicitud de actualización de roles
    public static class UpdateRolesRequest {
        private Set<String> roles;
        public Set<String> getRoles() {
            return roles;
        }
        public void setRoles(Set<String> roles) {
            this.roles = roles;
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/users/{userId}/roles")
    public ResponseEntity<?> updateUserRoles(@PathVariable Long userId, @RequestBody UpdateRolesRequest request) {
        log.info("PUT /api/auth/admin/users/{}/roles - Intentando actualizar roles para usuario ID: {}", userId, request.getRoles());
        try {
            Set<ERole> newRolesEnum = request.getRoles().stream()
                    .map(roleName -> {
                        try {
                            return ERole.valueOf(roleName);
                        } catch (IllegalArgumentException e) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol inválido proporcionado: " + roleName);
                        }
                    })
                    .collect(Collectors.toSet());

            User updatedUser = usuarioServicio.updateUserRoles(userId, newRolesEnum);
            return ResponseEntity.ok("Roles de usuario " + updatedUser.getUsername() + " actualizados exitosamente a: " + updatedUser.getRoles().stream().map(r -> r.getName().name()).collect(Collectors.joining(", ")));
        } catch (IllegalArgumentException e) {
            log.warn("Error al actualizar roles para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (UsernameNotFoundException e) {
            log.warn("Usuario no encontrado al actualizar roles para ID {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Error: Usuario no encontrado.");
        } catch (ResponseStatusException e) {
            log.warn("Error de validación de rol al actualizar roles para usuario {}: {}", userId, e.getReason());
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            log.error("Error inesperado al actualizar roles para usuario ID {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error interno al actualizar roles.");
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/users/{userId}/activate")
    public ResponseEntity<?> activateUserByAdmin(@PathVariable Long userId) {
        log.info("PUT /api/auth/admin/users/{}/activate - Solicitud de activación de usuario para ID: {}", userId, userId);
        try {
            boolean activated = usuarioServicio.activateUser(userId);
            if (activated) {
                log.info("Usuario con ID {} activado exitosamente por admin.", userId);
                return ResponseEntity.ok("Usuario activado exitosamente.");
            } else {
                log.warn("Fallo en la activación de usuario ID {}: no encontrado o ya activo.", userId);
                return ResponseEntity.badRequest().body("Error: Usuario no encontrado o ya activo.");
            }
        } catch (UsernameNotFoundException e) {
            log.warn("Usuario con ID {} no encontrado para activación por admin.", userId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Error: Usuario no encontrado.");
        } catch (Exception e) {
            log.error("Error inesperado al activar usuario ID {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error interno al activar usuario.");
        }
    }


    private Optional<User> obtenerUsuarioAutenticado(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        return usuarioServicio.findByUsername(auth.getName());
    }
}