package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Repositorio.UsuarioRepositorio;
import org.springframework.beans.factory.annotation.Autowired;
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

@Service
public class UsuarioServicio implements UserDetailsService {

    @Autowired
    private UsuarioRepositorio usuarioRepositorio;

    // --- NECESARIO: Inyectar PasswordEncoder ---
    @Autowired
    private PasswordEncoder passwordEncoder;
    // --- FIN Inyectar ---


    public Optional<User> findByUsername(String username) {
        return usuarioRepositorio.findByUsername(username);
    }

    @Transactional
    public User guardarUsuarioConRetorno(User usuario) {
        return usuarioRepositorio.save(usuario);
    }

    @Transactional
    public boolean validateUser(String token) {
        Optional<User> userOpt = usuarioRepositorio.findByValidationToken(token);
        if (userOpt.isPresent()) {
            User usuario = userOpt.get();
            if (usuario.getActive()) {
                System.out.println("Cuenta ya estaba activa para token (limpiando token): " + token);
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

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 1. Obtener tu entidad de usuario personalizada
        com.example.reservafutbol.Modelo.User usuario = usuarioRepositorio.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con email: " + username));

        // 2. Verificar si la cuenta está activa (lanza LockedException si no lo está)
        // Esta verificación ya impide el login si está inactiva.
        if (!usuario.getActive()) {
            System.out.println(">>> Intento de login denegado - cuenta inactiva: " + username);
            throw new LockedException("La cuenta no está activa. Por favor, valida tu correo electrónico.");
        }

        // 3. Crear la lista de autoridades/roles para Spring Security
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol()));

        // 4. Crear y devolver el objeto UserDetails de Spring Security
        // Utilizando el constructor: User(username, password, authorities)
        return new org.springframework.security.core.userdetails.User(
                usuario.getUsername(),
                usuario.getPassword(),
                authorities // Pasamos la lista de autoridades creada
        );
    }

    // Puede quedar si se usa en otro lado
    public void guardarUsuario(User usuario) {
        usuarioRepositorio.save(usuario);
    }

    // --- Crear Token de Reseteo (Sin cambios) ---
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
    // --- FIN Crear Token ---


    // --- Resetear Contraseña (Usa el passwordEncoder inyectado) ---
    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        Optional<User> userOpt = usuarioRepositorio.findByPasswordResetToken(token);
        if (userOpt.isEmpty()) {
            System.err.println("Intento de reseteo con token inválido: " + token);
            return false;
        }
        User usuario = userOpt.get();

        if (usuario.getPasswordResetTokenExpiry() == null || usuario.getPasswordResetTokenExpiry().isBefore(LocalDateTime.now())) {
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

        // --- AHORA SÍ PUEDE USAR passwordEncoder ---
        usuario.setPassword(passwordEncoder.encode(newPassword));
        usuario.setPasswordResetToken(null);
        usuario.setPasswordResetTokenExpiry(null);
        usuarioRepositorio.save(usuario);
        // --- FIN USO ---

        System.out.println(">>> Contraseña reseteada exitosamente para usuario: " + usuario.getUsername());
        return true;
    }
    // --- FIN Resetear Contraseña ---
}