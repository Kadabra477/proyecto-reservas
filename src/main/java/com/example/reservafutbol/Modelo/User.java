package com.example.reservafutbol.Modelo;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
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
@Table(name = "users",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "username") // username (que ahora es el email) debe ser único
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- CAMBIO CRÍTICO: username ahora almacena el email ---
    @NotBlank(message = "El correo electrónico es obligatorio")
    @Size(max = 50, message = "El correo electrónico no puede exceder los 50 caracteres")
    @Email(message = "Formato de correo electrónico inválido")
    @Column(nullable = false, unique = true)
    private String username; // Este campo ahora es el EMAIL y se usa para el login y contacto

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 6, max = 120, message = "La contraseña es demasiado larga (max 120 caracteres)")
    @Column(nullable = false)
    private String password;

    // --- Campo de Perfil: nombreCompleto (para mostrar) ---
    @NotBlank(message = "El nombre completo es obligatorio")
    private String nombreCompleto;

    // --- Otros Campos de Perfil (se mantienen) ---
    private String ubicacion;
    private Integer edad;
    @Builder.Default
    private Boolean completoPerfil = false;
    private String telefono;
    @Column(columnDefinition = "TEXT")
    private String bio;
    private String profilePictureUrl;

    // --- Campos para Manejo de Estado y Seguridad (se mantienen) ---
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @Builder.Default
    private boolean enabled = false;
    @Column(length = 36)
    private String verificationToken;

    @Column(length = 36)
    private String resetPasswordToken;

    private LocalDateTime resetPasswordTokenExpiryDate;


    // Constructor básico para registro (adaptado: username es el email)
    public User(String username, String password, String nombreCompleto) {
        this.username = username; // username es el email aquí
        this.password = password;
        this.nombreCompleto = nombreCompleto; // nombreCompleto es un campo de perfil
        this.enabled = false;
    }

    // --- Implementación de la interfaz UserDetails (se mantiene) ---
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().name()))
                .collect(Collectors.toList());
    }

    @Override
    public String getUsername() {
        return username; // Retorna el email del usuario para Spring Security
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }
}