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

    @Autowired(required = false)
    private S3StorageService s3StorageService;

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
    public List<User> findAllUsers() {
        log.info("Listando todos los usuarios (habilitados e inhabilitados).");
        return usuarioRepositorio.findAll();
    }

    @Transactional
    public User registerNewUser(User user) {
        user.setEnabled(false);
        user.setVerificationToken(null);

        if (user.getCompletoPerfil() == null) {
            user.setCompletoPerfil(false);
        }

        Set<Role> roles = new HashSet<>();
        Role userRole = roleRepositorio.findByName(ERole.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("Error: Rol de usuario no encontrado."));
        roles.add(userRole);
        user.setRoles(roles);

        User savedUser = usuarioRepositorio.save(user);
        log.info("Usuario '{}' registrado y guardado (PENDIENTE DE ACTIVACIÓN ADMIN).", savedUser.getUsername());

        return savedUser;
    }

    // NUEVO MÉTODO: Registra un usuario de OAuth2 con las propiedades de Google.
    // Este método es invocado desde SecurityConfig.
    @Transactional
    public User registerOAuth2User(User user) {
        user.setEnabled(true);
        user.setVerificationToken(null);
        user.setCompletoPerfil(true);

        Set<Role> roles = new HashSet<>();
        Role userRole = roleRepositorio.findByName(ERole.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("Error: Rol de usuario no encontrado."));
        roles.add(userRole);
        user.setRoles(roles);

        User savedUser = usuarioRepositorio.save(user);
        log.info("Usuario '{}' de OAuth2 registrado y activado.", savedUser.getUsername());
        return savedUser;
    }

    @Transactional
    public boolean activateUser(Long userId) {
        log.info("Intentando activar usuario con ID: {} (por administrador).", userId);
        Optional<User> userOptional = usuarioRepositorio.findById(userId);
        if (userOptional.isPresent()) {
            User usuario = userOptional.get();
            if (usuario.isEnabled()) {
                log.warn("Usuario con ID {} ya estaba activado.", userId);
                return true;
            }
            usuario.setEnabled(true);
            usuario.setVerificationToken(null);
            usuarioRepositorio.save(usuario);
            log.info("Usuario {} (ID {}) activado exitosamente por administrador.", usuario.getUsername(), userId);
            return true;
        }
        log.warn("Usuario con ID {} no encontrado para activación por administrador.", userId);
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
        usuarioRepositorio.save(user);
        log.info("Perfil actualizado correctamente para usuario: {}", user.getUsername());
    }

    @Transactional
    public User updateUserRoles(Long userId, Set<ERole> newRolesEnum) {
        User user = usuarioRepositorio.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con ID: " + userId));

        Set<Role> rolesToSet = new HashSet<>();

        boolean hasAdmin = newRolesEnum.contains(ERole.ROLE_ADMIN);
        boolean hasComplexOwner = newRolesEnum.contains(ERole.ROLE_COMPLEX_OWNER);
        boolean hasUser = newRolesEnum.contains(ERole.ROLE_USER);

        if (hasAdmin && hasComplexOwner) {
            throw new IllegalArgumentException("Un usuario no puede tener los roles ADMIN y COMPLEX_OWNER a la vez.");
        }

        if (hasAdmin) {
            rolesToSet.add(roleRepositorio.findByName(ERole.ROLE_ADMIN)
                    .orElseThrow(() -> new RuntimeException("Error: Rol 'ADMIN' no encontrado.")));
            rolesToSet.add(roleRepositorio.findByName(ERole.ROLE_USER)
                    .orElseThrow(() -> new RuntimeException("Error: Rol 'USER' no encontrado.")));
        }
        else if (hasComplexOwner) {
            rolesToSet.add(roleRepositorio.findByName(ERole.ROLE_COMPLEX_OWNER)
                    .orElseThrow(() -> new RuntimeException("Error: Rol 'COMPLEX_OWNER' no encontrado.")));
            rolesToSet.add(roleRepositorio.findByName(ERole.ROLE_USER)
                    .orElseThrow(() -> new RuntimeException("Error: Rol 'USER' no encontrado.")));
        }
        else if (hasUser || newRolesEnum.isEmpty()) {
            rolesToSet.add(roleRepositorio.findByName(ERole.ROLE_USER)
                    .orElseThrow(() -> new RuntimeException("Error: Rol 'USER' no encontrado.")));
        } else {
            // Lógica para roles que no sean ADMIN, COMPLEX_OWNER o USER.
            for (ERole roleName : newRolesEnum) {
                rolesToSet.add(roleRepositorio.findByName(roleName)
                        .orElseThrow(() -> new RuntimeException("Error: Rol '" + roleName.name() + "' no encontrado.")));
            }
        }

        user.setRoles(rolesToSet);
        log.info("Roles actualizados para usuario {}: {}", user.getUsername(), rolesToSet.stream().map(r -> r.getName().name()).collect(Collectors.joining(", ")));
        return usuarioRepositorio.save(user);
    }

    @Transactional(readOnly = true)
    public boolean existsOtherAdmin(Long currentAdminId) {
        log.debug("Verificando si existen otros administradores además de ID: {}", currentAdminId);
        Role adminRole = roleRepositorio.findByName(ERole.ROLE_ADMIN)
                .orElseThrow(() -> new RuntimeException("Error: Rol ADMIN no encontrado."));
        List<User> admins = usuarioRepositorio.findByRolesContaining(adminRole);

        long otherAdminsCount = admins.stream()
                .filter(admin -> !admin.getId().equals(currentAdminId))
                .count();

        return otherAdminsCount > 0;
    }

    @Transactional
    public void deleteUser(Long userId) {
        Optional<User> userOptional = usuarioRepositorio.findById(userId);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            // Lógica para eliminar la foto de perfil del usuario si aplica
            // if (user.getProfilePictureUrl() != null && !user.getProfilePictureUrl().isEmpty()) {
            //     try {
            //         s3StorageService.deleteFile(user.getProfilePictureUrl());
            //         log.info("Foto de perfil de usuario {} eliminada de S3.", user.getUsername());
            //     } catch (Exception e) {
            //         log.error("Error al eliminar foto de perfil de S3 para usuario {}: {}", user.getUsername(), e.getMessage());
            //     }
            // }
            usuarioRepositorio.delete(user);
            log.info("Usuario {} (ID {}) eliminado exitosamente.", user.getUsername(), userId);
        } else {
            log.warn("Intento de eliminar usuario con ID {} fallido: No encontrado.", userId);
        }
    }
}