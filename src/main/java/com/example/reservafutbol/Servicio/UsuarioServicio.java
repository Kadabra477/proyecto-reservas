package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Repositorio.UsuarioRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID; // Necesario para generar token

@Service
public class UsuarioServicio implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioServicio.class);

    @Autowired
    private UsuarioRepositorio usuarioRepositorio;

    @Autowired
    private EmailService emailService;

    @Override
    @Transactional(readOnly = true) // Método de solo lectura
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Asumo que 'username' es el email o un campo único que se usa para el login
        log.debug("Intentando cargar usuario por username/email: {}", username);
        User usuario = usuarioRepositorio.findByUsername(username) // Intenta buscar por username
                .orElseGet(() -> usuarioRepositorio.findByEmail(username) // Si no lo encuentra por username, intenta por email
                        .orElseThrow(() -> {
                            log.warn("Usuario no encontrado con username o email: {}", username);
                            return new UsernameNotFoundException("Usuario no encontrado: " + username);
                        }));
        log.debug("Usuario {} cargado exitosamente. Habilitado: {}", usuario.getUsername(), usuario.isEnabled());
        return usuario; // Retorna el objeto User, que implementa UserDetails
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        log.debug("Buscando usuario por username: {}", username);
        return usuarioRepositorio.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        log.debug("Buscando usuario por email: {}", email);
        return usuarioRepositorio.findByEmail(email);
    }

    @Transactional
    public User save(User user) {
        log.info("Guardando usuario: {}", user.getUsername());
        return usuarioRepositorio.save(user);
    }

    // Método para registrar un nuevo usuario (ahora con el nuevo modelo User.java)
    @Transactional
    public User registerNewUser(User user) {
        // Asegurarse de que el usuario no esté habilitado por defecto y generar token de verificación
        user.setEnabled(false); // Campo 'enabled' en el nuevo User.java
        user.setVerificationToken(UUID.randomUUID().toString()); // Campo 'verificationToken' en el nuevo User.java
        // Por defecto, completoPerfil puede ser false al registrar
        if (user.getCompletoPerfil() == null) {
            user.setCompletoPerfil(false);
        }

        User savedUser = usuarioRepositorio.save(user);
        log.info("Usuario registrado y guardado: {}", savedUser.getUsername());

        // Enviar email de validación
        emailService.sendValidationEmail(savedUser.getEmail(), savedUser.getVerificationToken());
        log.info("Email de validación enviado a {}", savedUser.getEmail());
        return savedUser;
    }

    // Método para activar cuenta (verificación de email)
    @Transactional
    public boolean activateUser(String token) {
        log.info("Intentando activar usuario con token: {}", token);
        Optional<User> userOptional = usuarioRepositorio.findByVerificationToken(token);
        if (userOptional.isPresent()) {
            User usuario = userOptional.get();
            // Verificar si la cuenta ya está activada
            if (usuario.isEnabled()) { // Usar .isEnabled() del modelo User
                log.warn("Usuario con token {} ya estaba activado.", token);
                return true; // Considerar ya activado si enabled es true
            }
            usuario.setEnabled(true); // Cambiar enabled a true
            usuario.setVerificationToken(null); // Borrar el token de verificación
            usuarioRepositorio.save(usuario);
            log.info("Usuario {} activado exitosamente.", usuario.getUsername());
            return true;
        }
        log.warn("Token de activación no encontrado o inválido: {}", token);
        return false;
    }

    // Método para generar y enviar token de reseteo de contraseña
    @Transactional
    public void createPasswordResetTokenForUser(String email) {
        log.info("Creando token de reseteo de contraseña para email: {}", email);
        Optional<User> userOptional = usuarioRepositorio.findByEmail(email);
        if (userOptional.isEmpty()) {
            log.warn("Usuario no encontrado para reseteo de contraseña: {}", email);
            throw new UsernameNotFoundException("Usuario no encontrado con email: " + email);
        }
        User usuario = userOptional.get();
        String token = UUID.randomUUID().toString();
        usuario.setResetPasswordToken(token); // Usar setResetPasswordToken
        usuario.setResetPasswordTokenExpiryDate(LocalDateTime.now().plusHours(1)); // Usar setResetPasswordTokenExpiryDate
        usuarioRepositorio.save(usuario);
        emailService.sendPasswordResetEmail(usuario.getEmail(), token);
        log.info("Email de reseteo de contraseña enviado a: {}", email);
    }

    // Método para validar token de reseteo y cambiar contraseña
    @Transactional
    public Optional<User> validatePasswordResetToken(String token) {
        log.debug("Validando token de reseteo: {}", token);
        Optional<User> userOptional = usuarioRepositorio.findByResetPasswordToken(token);
        if (userOptional.isPresent()) {
            User usuario = userOptional.get();
            // Verificar expiración del token
            if (usuario.getResetPasswordTokenExpiryDate() != null && usuario.getResetPasswordTokenExpiryDate().isAfter(LocalDateTime.now())) {
                log.info("Token de reseteo {} válido para usuario {}", token, usuario.getUsername());
                return Optional.of(usuario);
            } else {
                log.warn("Token de reseteo {} expirado o inválido para usuario {}", token, usuario.getUsername());
            }
        } else {
            log.warn("Token de reseteo {} no encontrado.", token);
        }
        return Optional.empty();
    }

    // Método para actualizar contraseña
    @Transactional
    public void updatePassword(User user, String newPassword) {
        log.info("Actualizando contraseña para usuario: {}", user.getUsername());
        user.setPassword(newPassword);
        user.setResetPasswordToken(null); // Limpiar token de reseteo
        user.setResetPasswordTokenExpiryDate(null); // Limpiar fecha de expiración
        usuarioRepositorio.save(user);
        log.info("Contraseña actualizada exitosamente para usuario {}", user.getUsername());
    }
}