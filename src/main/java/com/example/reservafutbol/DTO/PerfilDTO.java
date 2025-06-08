package com.example.reservafutbol.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List; // Importar List

@Data
@NoArgsConstructor // Para que Jackson pueda deserializarlo desde JSON
@AllArgsConstructor // Para facilitar la construcción al enviar datos
public class PerfilDTO {

    private String nombreCompleto;
    private String ubicacion;
    private Integer edad;
    private String bio;
    private String email; // Para GET del perfil, el frontend espera el email
    private String profilePictureUrl; // Para GET del perfil, el frontend espera la URL de la imagen
    private List<String> roles; // ¡NUEVO CAMPO: Lista de roles del usuario!
}