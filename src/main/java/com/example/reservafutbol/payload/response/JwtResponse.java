package com.example.reservafutbol.payload.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class JwtResponse {
    private String token;
    private String type = "Bearer";
    private Long id;
    private String username; // Este campo ahora es el email del usuario (login ID)
    private String nombreCompleto; // Nombre completo del usuario
    private String role;

    // Constructor adaptado a los nuevos campos de User y RegisterRequest
    public JwtResponse(String token, Long id, String username, String nombreCompleto, String role) {
        this.token = token;
        this.id = id;
        this.username = username; // Ahora el username del JWT es el email
        this.nombreCompleto = nombreCompleto;
        this.role = role;
    }
}