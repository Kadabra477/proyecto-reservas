package com.example.reservafutbol.Modelo;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder; // Asegúrate de importar Builder si usas @Builder
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "users", // Renombrado a 'users' por convención y para evitar conflictos con 'usuarios'
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "username"), // Si 'username' es para login
                @UniqueConstraint(columnNames = "email") // Si 'email' es distinto de username
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder // Se mantiene @Builder si quieres usar el patrón Builder
public class User implements UserDetails { // Implementa UserDetails, ¡CRÍTICO para Spring Security!

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- Campos de Autenticación Básicos ---
    @NotBlank(message = "El nombre de usuario es obligatorio")
    @Size(max = 20, message = "El nombre de usuario no puede exceder los 20 caracteres")
    @Column(nullable = false, unique = true)
    private String username; // Este es el campo que Spring Security usará para el login

    @NotBlank(message = "El email es obligatorio")
    @Size(max = 50, message = "El email no puede exceder los 50 caracteres")
    @Email(message = "Formato de email inválido")
    @Column(nullable = false, unique = true)
    private String email; // El email real del usuario, diferente del username si se desea

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(max = 120, message = "La contraseña es demasiado larga (max 120 caracteres)")
    @Column(nullable = false)
    private String password;

    // --- Campos de Perfil de Usuario ---
    private String nombreCompleto;

    private String ubicacion;
    private Integer edad;
    private Boolean completoPerfil = false; // Indica si el usuario ha completado su perfil
    private String telefono; // Número de teléfono del usuario

    @Column(columnDefinition = "TEXT") // Para almacenar texto más largo
    private String bio; // Biografía del usuario

    private String profilePictureUrl; // URL de la foto de perfil (ej. en S3)

    // --- Campos para Manejo de Estado y Seguridad (activación, roles, reseteo de password) ---
    @ManyToMany(fetch = FetchType.EAGER) // Carga los roles inmediatamente con el usuario
    @JoinTable(name = "user_roles", // Tabla intermedia para la relación ManyToMany
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>(); // Uso de Set<Role> para roles, ¡CRÍTICO!

    private boolean enabled = false; // Estado de habilitación de la cuenta (para verificación de email)
    @Column(length = 36) // Longitud típica para UUIDs
    private String verificationToken; // Token para verificación de email

    @Column(length = 36)
    private String resetPasswordToken; // Token para restablecimiento de contraseña

    private LocalDateTime resetPasswordTokenExpiryDate; // Fecha de expiración del token de restablecimiento


    // Constructor básico para registro
    public User(String username, String email, String password, String nombreCompleto) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.nombreCompleto = nombreCompleto;
        this.enabled = false; // Por defecto no habilitado hasta verificar email
    }

    // --- Implementación de la interfaz UserDetails (¡CRÍTICO para Spring Security!) ---
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().name()))
                .collect(Collectors.toList());
    }

    @Override
    public String getUsername() {
        return username; // Retorna el campo 'username' para Spring Security
    }

    @Override
    public String getPassword() {
        return password; // Retorna el campo 'password' para Spring Security
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // Si no manejas expiración de cuentas, deja en true
    }

    @Override
    public boolean isAccountNonLocked() {
        return true; // Si no manejas bloqueo de cuentas, deja en true
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // Si no manejas expiración de credenciales, deja en true
    }

    @Override
    public boolean isEnabled() {
        return this.enabled; // Retorna el estado de habilitación de la cuenta
    }
}