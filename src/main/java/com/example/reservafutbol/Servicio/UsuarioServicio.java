package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Repositorio.UsuarioRepositorio;
import com.example.reservafutbol.Modelo.ERole;
import com.example.reservafutbol.Modelo.Role;
import com.example.reservafutbol.Repositorio.RoleRepositorio;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.MessagingException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Service
public class UsuarioServicio implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioServicio.class);

    @Autowired
    private UsuarioRepositorio usuarioRepositorio;

    @Autowired
    private EmailService emailService;

    @Autowired
    private RoleRepositorio roleRepositorio;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Intentando cargar usuario por email (username): {}", username);
        User usuario = usuarioRepositorio.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Usuario no encontrado con email (username): {}", username);
                    return new UsernameNotFoundException("Usuario no encontrado: " + username);
                });
        log.debug("Usuario {} cargado exitosamente. Habilitado: {}", usuario.getUsername(), usuario.isEnabled());
        return usuario;
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        log.debug("Buscando usuario por email (username): {}", username);
        return usuarioRepositorio.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        log.debug("Buscando usuario por ID: {}", id);
        return usuarioRepositorio.findById(id);
    }

    @Transactional(readOnly = true)
    public List<User> findAllEnabledUsers() {
        log.info("Listando todos los usuarios habilitados.");
        return usuarioRepositorio.findAllByEnabled(true);
    }

    @Transactional
    public User save(User user) {
        log.info("Guardando usuario: {}", user.getUsername());
        return usuarioRepositorio.save(user);
    }

    @Transactional
    public User registerNewUser(User user) {
        user.setEnabled(false);
        user.setVerificationToken(UUID.randomUUID().toString());
        if (user.getCompletoPerfil() == null) {
            user.setCompletoPerfil(false);
        }

        Set<Role> roles = new HashSet<>();
        Role userRole = roleRepositorio.findByName(ERole.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("Error: Rol de usuario no encontrado."));
        roles.add(userRole);
        user.setRoles(roles);

        User savedUser = usuarioRepositorio.save(user);
        log.info("Usuario registrado y guardado: {}", savedUser.getUsername());

        try {
            emailService.sendVerificationEmail(savedUser.getUsername(), savedUser.getNombreCompleto(), savedUser.getVerificationToken());
            log.info("Email de validación enviado a {}", savedUser.getUsername());
        } catch (MessagingException e) {
            log.error("Error al enviar email de verificación a {}: {}", savedUser.getUsername(), e.getMessage());
            throw new RuntimeException("Fallo al enviar el email de verificación.", e);
        }
        return savedUser;
    }

    @Transactional
    public boolean activateUser(String token) {
        log.info("Intentando activar usuario con token: {}", token);
        Optional<User> userOptional = usuarioRepositorio.findByVerificationToken(token);
        if (userOptional.isPresent()) {
            User usuario = userOptional.get();
            if (usuario.isEnabled()) {
                log.warn("Usuario con token {} ya estaba activado.", token);
                return true;
            }
            usuario.setEnabled(true);
            usuario.setVerificationToken(null);
            usuarioRepositorio.save(usuario);
            log.info("Usuario {} activado exitosamente.", usuario.getUsername());
            return true;
        }
        log.warn("Token de activación no encontrado o inválido: {}", token);
        return false;
    }

    @Transactional
    public void createPasswordResetTokenForUser(String email) {
        log.info("Creando token de reseteo de contraseña para email (username): {}", email);
        User user = usuarioRepositorio.findByUsername(email)
                .orElseThrow(() -> {
                    log.warn("Usuario no encontrado para reseteo de contraseña: {}", email);
                    return new UsernameNotFoundException("Usuario no encontrado con email: " + email);
                });
        String token = UUID.randomUUID().toString();
        user.setResetPasswordToken(token);
        user.setResetPasswordTokenExpiryDate(LocalDateTime.now().plusHours(1));
        usuarioRepositorio.save(user);
        try {
            emailService.sendPasswordResetEmail(user.getUsername(), token);
            log.info("Link de reseteo de contraseña enviado a: {}", email);
        } catch (MessagingException e) {
            log.error("Error al enviar email de reseteo de contraseña a {}: {}", email, e.getMessage());
            throw new RuntimeException("Fallo al enviar el email de reseteo de contraseña.", e);
        }
    }

    @Transactional
    public Optional<User> validatePasswordResetToken(String token) {
        log.debug("Validando token de reseteo: {}", token);
        Optional<User> userOptional = usuarioRepositorio.findByResetPasswordToken(token);
        if (userOptional.isPresent()) {
            User usuario = userOptional.get();
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

    @Transactional
    public void updatePassword(User user, String newPassword) {
        log.info("Actualizando contraseña para usuario: {}", user.getUsername());
        user.setPassword(newPassword);
        user.setResetPasswordToken(null);
        user.setResetPasswordTokenExpiryDate(null);
        usuarioRepositorio.save(user);
        log.info("Contraseña actualizada exitosamente para usuario {}", user.getUsername());
    }

    @Transactional
    // <-- CAMBIO: Eliminado el parámetro 'telefono' -->
    public void updateUserProfile(User user, String nombreCompleto, String ubicacion, Integer edad, String bio) {
        log.info("Actualizando perfil del usuario: {}", user.getUsername());

        if (nombreCompleto != null) {
            user.setNombreCompleto(nombreCompleto);
        }
        if (ubicacion != null) {
            user.setUbicacion(ubicacion);
        }
        if (edad != null) {
            user.setEdad(edad);
        }
        if (bio != null) {
            user.setBio(bio);
        }
        // <-- ELIMINAR: Ya no se maneja el campo telefono aquí -->
        // if (telefono != null) {
        //     user.setTelefono(telefono);
        // }

        usuarioRepositorio.save(user);
        log.info("Perfil actualizado correctamente para usuario: {}", user.getUsername());
    }

    @Transactional
    public void updateProfilePictureUrl(User user, String url) {
        log.info("Actualizando foto de perfil para usuario: {}", user.getUsername());

        user.setProfilePictureUrl(url);
        usuarioRepositorio.save(user);

        log.info("Foto de perfil actualizada para usuario: {}", user.getUsername());
    }

    // --- Método updateUserRoles (ya implementado y corregido) ---
    @Transactional
    public User updateUserRoles(Long userId, Set<ERole> newRolesEnum) {
        User user = usuarioRepositorio.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con ID: " + userId));

        Set<Role> rolesToSet = new HashSet<>();

        boolean hasAdmin = newRolesEnum.contains(ERole.ROLE_ADMIN);
        boolean hasComplexOwner = newRolesEnum.contains(ERole.ROLE_COMPLEX_OWNER);

        if (hasAdmin && hasComplexOwner) {
            throw new IllegalArgumentException("Un usuario no puede tener los roles ADMIN y COMPLEX_OWNER a la vez.");
        }

        for (ERole newRoleEnum : newRolesEnum) {
            Role role = roleRepositorio.findByName(newRoleEnum)
                    .orElseThrow(() -> new RuntimeException("Error: Rol '" + newRoleEnum + "' no encontrado."));
            rolesToSet.add(role);
        }

        if (hasAdmin) {
            log.info("Asignando ROLE_ADMIN a usuario {}. Eliminando ROLE_COMPLEX_OWNER si existe.", user.getUsername());
            rolesToSet.removeIf(r -> r.getName().equals(ERole.ROLE_COMPLEX_OWNER));
        } else if (hasComplexOwner) {
            log.info("Asignando ROLE_COMPLEX_OWNER a usuario {}. Eliminando ROLE_ADMIN si existe.", user.getUsername());
            rolesToSet.removeIf(r -> r.getName().equals(ERole.ROLE_ADMIN));
        }

        if (rolesToSet.isEmpty()) {
            Role userRole = roleRepositorio.findByName(ERole.ROLE_USER)
                    .orElseThrow(() -> new RuntimeException("Error: Rol de usuario no encontrado."));
            rolesToSet.add(userRole);
            log.warn("Usuario {} quedó sin roles específicos, se le asignó ROLE_USER por defecto.", user.getUsername());
        }

        user.setRoles(rolesToSet);
        log.info("Roles actualizados para usuario {}: {}", user.getUsername(), rolesToSet.stream().map(r -> r.getName().name()).collect(Collectors.joining(", ")));
        return usuarioRepositorio.save(user);
    }
}