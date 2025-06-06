package com.example.reservafutbol.Modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "roles")
@Getter @Setter @NoArgsConstructor
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING) // Guarda el enum como String (ej. "ROLE_USER", "ROLE_ADMIN")
    @Column(length = 20)
    private ERole name; // Enum para los nombres de roles

    public Role(ERole name) {
        this.name = name;
    }
}