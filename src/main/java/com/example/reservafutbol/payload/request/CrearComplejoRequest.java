package com.example.reservafutbol.payload.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;
import java.util.Map;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrearComplejoRequest {
    @NotBlank(message = "El nombre del complejo es obligatorio")
    private String nombre;

    @NotBlank(message = "El email del propietario es obligatorio")
    @Email(message = "El email del propietario debe ser válido.")
    private String propietarioUsername;

    private String descripcion;
    private String ubicacion;
    private String telefono;
    // ¡Campo 'fotoUrl' ELIMINADO de aquí! La imagen se enviará como MultipartFile.
    private LocalTime horarioApertura;
    private LocalTime horarioCierre;

    @NotNull(message = "Debe especificar la cantidad de canchas por tipo.")
    private Map<String, Integer> canchaCounts;

    @NotNull(message = "Debe especificar los precios por hora de las canchas.")
    private Map<String, Double> canchaPrices;

    @NotNull(message = "Debe especificar las superficies de las canchas.")
    private Map<String, String> canchaSurfaces;

    @NotNull(message = "Debe especificar si las canchas tienen iluminación.")
    private Map<String, Boolean> canchaIluminacion;

    @NotNull(message = "Debe especificar si las canchas tienen techo.")
    private Map<String, Boolean> canchaTecho;
}