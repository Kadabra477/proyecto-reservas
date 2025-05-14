package com.example.reservafutbol.Modelo;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String rol;

    @Column(nullable = false, unique = true)
    private String username; // Email

    @Column(nullable = false)
    private String password;

    private String nombreCompleto;

    private String ubicacion;
    private Integer edad;
    private Boolean completoPerfil = false;
    private String telefono;

    @Column(length = 36)
    private String validationToken;

    private Boolean active = false;

    @Column(length = 36)
    private String passwordResetToken;

    private LocalDateTime passwordResetTokenExpiry;
}
