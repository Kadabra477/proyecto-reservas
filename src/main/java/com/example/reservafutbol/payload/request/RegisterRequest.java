package com.example.reservafutbol.payload.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    // ELIMINADO: Ya no se envía un username separado desde el frontend al registrar
    // private String username;

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Size(max = 50, message = "El correo electrónico no puede exceder los 50 caracteres")
    @Email(message = "Formato de correo electrónico inválido")
    private String email; // Este campo ahora será el identificador de login (username en User)

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 6, max = 40, message = "La contraseña debe tener entre 6 y 40 caracteres")
    private String password;

    @NotBlank(message = "El nombre completo es obligatorio")
    private String nombreCompleto; // Nombre completo para mostrar en el perfil

    // Campos opcionales para el perfil (se mantienen)
    private String ubicacion;
    private Integer edad;
    private String telefono;
    private String bio;
}