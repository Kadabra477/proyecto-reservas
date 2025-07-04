package com.example.reservafutbol.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor // Para que Jackson pueda deserializarlo desde JSON
@AllArgsConstructor // Para facilitar la construcción al enviar datos
public class PerfilDTO {

    private String nombreCompleto; // Mantener por compatibilidad si es necesario
    private String nombre;       // Campo para el nombre (primer nombre)
    private String apellido;     // Campo para el apellido (resto del nombre completo)
    private String ubicacion;
    private Integer edad;
    private String bio;
    private String email; // Se mapea desde el 'username' del User
    // ¡Campo 'profilePictureUrl' ELIMINADO!
    private List<String> roles; // Lista de roles del usuario
}