package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Repositorio.UsuarioRepositorio;
import lombok.RequiredArgsConstructor;
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

@RequiredArgsConstructor  // Lombok genera constructor con todos los final
@Service
public class UsuarioServicio implements UserDetailsService {

    private final UsuarioRepositorio usuarioRepositorio;
    private final PasswordEncoder passwordEncoder;

    // --- Buscar usuario por username/email ---
    public Optional<User> findByUsername(String username) {
        return usuarioRepositorio.findByUsername(username);
    }

    // --- Guardar usuario (sin retorno) ---
    @Transactional
    public void guardarUsuario(User usuario) {
        usuarioRepositorio.save(usuario);
    }

    // --- Guardar usuario (con retorno) ---
    @Transactional
    public User guardarUsuarioConRetorno(User usuario) {
        return usuarioRepositorio.save(usuario);
    }

    // --- Validar token de activación ---
    @Transactional
    public boolean validateUser(String token) {
        Optional<User> userOpt = usuarioRepositorio.findByValidationToken(token);
        if (userOpt.isPresent()) {
            User usuario = userOpt.get();
            if (usuario.getActive()) {
                System.out.println("Cuenta ya activa para token (limpiando token): " + token);
                usuario.setValidationToken(null);
                usuarioRepositorio.save(usuario);
                return true;
            }
            usuario.setActive(true);
            usuario.setValidationToken(null);
            usuarioRepositorio.save(usuario);
            System.out.println(">>> Cuenta activada para usuario: " + usuario.getUsername());
            return true;
        }
        System.err.println(">>> Token de validación no encontrado: " + token);
        return false;
    }

    // --- Cargar usuario para Spring Security ---
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User usuario = usuarioRepositorio.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con email: " + username));

        if (!usuario.getActive()) {
            System.out.println(">>> Intento de login denegado - cuenta inactiva: " + username);
            throw new LockedException("La cuenta no está activa. Por favor, valida tu correo electrónico.");
        }

        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + usuario.getRol()));

        return new org.springframework.security.core.userdetails.User(
                usuario.getUsername(),
                usuario.getPassword(),
                authorities
        );
    }

    // --- Crear token para reseteo de contraseña ---
    @Transactional
    public String createPasswordResetToken(String userEmail) {
        Optional<User> userOpt = usuarioRepositorio.findByUsername(userEmail);
        if (userOpt.isEmpty()) {
            System.err.println("Solicitud de reseteo para email no encontrado: " + userEmail);
            return null;
        }
        User usuario = userOpt.get();
        String token = UUID.randomUUID().toString();
        LocalDateTime expiryDate = LocalDateTime.now().plusHours(1);
        usuario.setPasswordResetToken(token);
        usuario.setPasswordResetTokenExpiry(expiryDate);
        usuarioRepositorio.save(usuario);
        System.out.println(">>> Token de reseteo creado para: " + userEmail);
        return token;
    }

    // --- Resetear contraseña usando token ---
    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        Optional<User> userOpt = usuarioRepositorio.findByPasswordResetToken(token);
        if (userOpt.isEmpty()) {
            System.err.println("Intento de reseteo con token inválido: " + token);
            return false;
        }
        User usuario = userOpt.get();

        if (usuario.getPasswordResetTokenExpiry() == null ||
                usuario.getPasswordResetTokenExpiry().isBefore(LocalDateTime.now())) {
            System.err.println("Intento de reseteo con token expirado para usuario: " + usuario.getUsername());
            usuario.setPasswordResetToken(null);
            usuario.setPasswordResetTokenExpiry(null);
            usuarioRepositorio.save(usuario);
            return false;
        }

        if (newPassword == null || newPassword.length() < 6) {
            System.err.println("Intento de reseteo con contraseña inválida para usuario: " + usuario.getUsername());
            return false;
        }

        usuario.setPassword(passwordEncoder.encode(newPassword));
        usuario.setPasswordResetToken(null);
        usuario.setPasswordResetTokenExpiry(null);
        usuarioRepositorio.save(usuario);

        System.out.println(">>> Contraseña reseteada exitosamente para usuario: " + usuario.getUsername());
        return true;
    }

}