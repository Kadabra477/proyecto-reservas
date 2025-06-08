package com.example.reservafutbol.payload.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map; // Para el mapa de canchaCounts

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrearComplejoRequest {
    @NotBlank(message = "El nombre del complejo es obligatorio")
    private String nombre;

    @NotBlank(message = "El nombre de usuario (email) del propietario es obligatorio")
    private String propietarioUsername; // El username (email) del usuario que será dueño del complejo

    // Mapa para la cantidad de canchas por tipo (ej. {"Fútbol 5": 2})
    @NotNull(message = "Debe especificar la cantidad de canchas por tipo.")
    private Map<String, Integer> canchaCounts;
}