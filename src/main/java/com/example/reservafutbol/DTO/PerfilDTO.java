package com.example.reservafutbol.DTO;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor // Para que Jackson pueda deserializarlo desde JSON
@AllArgsConstructor // Para facilitar la construcción al enviar datos
public class PerfilDTO {

    private String nombreCompleto;
    private String ubicacion;
    private Integer edad;
    private String bio; // Nuevo campo
    private String email; // Para GET del perfil, el frontend espera el email
    private String profilePictureUrl; // Para GET del perfil, el frontend espera la URL de la imagen
}