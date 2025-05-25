package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Repositorio.UsuarioRepositorio;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class UsuarioServicio implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioServicio.class);

    private final UsuarioRepositorio usuarioRepositorio;
    private final PasswordEncoder passwordEncoder;

    public Optional<User> findByUsername(String username) {
        return usuarioRepositorio.findByUsername(username);
    }

    @Transactional
    public void guardarUsuario(User usuario) {
        usuarioRepositorio.save(usuario);
        log.info("Usuario guardado: {}", usuario.getUsername());
    }

    @Transactional
    public User guardarUsuarioConRetorno(User usuario) {
        User savedUser = usuarioRepositorio.save(usuario);
        log.info("Usuario guardado con retorno: {}", savedUser.getUsername());
        return savedUser;
    }

    @Transactional
    public boolean validateUser(String token) {
        Optional<User> userOpt = usuarioRepositorio.findByValidationToken(token);
        if (userOpt.isPresent()) {
            User usuario = userOpt.get();
            if (Boolean.TRUE.equals(usuario.getActive())) {
                log.info("Cuenta ya activa para token {}, limpiando token.", token);
                usuario.setValidationToken(null);
                usuarioRepositorio.save(usuario);
                return true;
            }
            usuario.setActive(true);
            usuario.setValidationToken(null);
            usuarioRepositorio.save(usuario);
            log.info("Cuenta activada para usuario: {}", usuario.getUsername());
            return true;
        }
        log.warn("Token de validación no encontrado: {}", token);
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User usuario = usuarioRepositorio.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con email: " + username));

        if (!Boolean.TRUE.equals(usuario.getActive())) {
            log.warn("Intento de login denegado - cuenta inactiva: {}", username);
            throw new LockedException("La cuenta no está activa. Por favor, valida tu correo electrónico.");
        }

        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + usuario.getRol()));

        return new org.springframework.security.core.userdetails.User(
                usuario.getUsername(),
                usuario.getPassword(),
                authorities);
    }

    @Transactional
    public String createPasswordResetToken(String userEmail) {
        Optional<User> userOpt = usuarioRepositorio.findByUsername(userEmail);
        if (userOpt.isEmpty()) {
            log.warn("Solicitud de reseteo para email no encontrado: {}", userEmail);
            return null;
        }
        User usuario = userOpt.get();
        String token = UUID.randomUUID().toString();
        LocalDateTime expiryDate = LocalDateTime.now().plusHours(1);
        usuario.setPasswordResetToken(token);
        usuario.setPasswordResetTokenExpiry(expiryDate);
        usuarioRepositorio.save(usuario);
        log.info("Token de reseteo creado para usuario: {}", userEmail);
        return token;
    }

    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        Optional<User> userOpt = usuarioRepositorio.findByPasswordResetToken(token);
        if (userOpt.isEmpty()) {
            log.warn("Intento de reseteo con token inválido: {}", token);
            return false;
        }
        User usuario = userOpt.get();

        if (usuario.getPasswordResetTokenExpiry() == null || usuario.getPasswordResetTokenExpiry().isBefore(LocalDateTime.now())) {
            log.warn("Intento de reseteo con token expirado para usuario: {}", usuario.getUsername());
            usuario.setPasswordResetToken(null);
            usuario.setPasswordResetTokenExpiry(null);
            usuarioRepositorio.save(usuario);
            return false;
        }

        if (newPassword == null || newPassword.length() < 6) {
            log.warn("Intento de reseteo con contraseña inválida para usuario: {}", usuario.getUsername());
            return false;
        }

        usuario.setPassword(passwordEncoder.encode(newPassword));
        usuario.setPasswordResetToken(null);
        usuario.setPasswordResetTokenExpiry(null);
        usuarioRepositorio.save(usuario);
        log.info("Contraseña reseteada exitosamente para usuario: {}", usuario.getUsername());
        return true;
    }

    // --- Nuevos métodos para el perfil ---

    @Transactional(readOnly = true)
    public Optional<User> getUserProfileByEmail(String email) {
        return usuarioRepositorio.findByUsername(email);
    }

    @Transactional
    public User updateUserProfile(User user, String nombreCompleto, String ubicacion, Integer edad, String bio) {
        if (nombreCompleto != null) {
            user.setNombreCompleto(nombreCompleto);
        }
        if (ubicacion != null) {
            user.setUbicacion(ubicacion);
        }
        // Solo actualizar edad si no es nula. Si viene 0, puede ser intencional o un error.
        // Se sugiere que el frontend envíe null o "" para "sin valor" y 0 para "cero años".
        // Aquí asumimos que null significa que no se debe actualizar.
        user.setEdad(edad); // Se permite null si el DTO lo envía como null
        if (bio != null) {
            user.setBio(bio);
        }
        user.setCompletoPerfil(true); // Una vez que edita, se asume que completa
        return usuarioRepositorio.save(user);
    }

    @Transactional
    public User updateProfilePictureUrl(User user, String profilePictureUrl) {
        user.setProfilePictureUrl(profilePictureUrl);
        return usuarioRepositorio.save(user);
    }
}