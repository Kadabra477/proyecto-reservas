package com.example.reservafutbol.DTO;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank; // Importar NotBlank
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class ReservaDTO {

    @NotNull(message = "El ID de la cancha es obligatorio")
    private Long canchaId;

    @NotNull(message = "La fecha es obligatoria")
    @Future(message = "La fecha debe ser en el futuro")
    private LocalDate fecha;

    @NotNull(message = "La hora es obligatoria")
    private LocalTime hora;

    private String nombre;
    private String apellido;
    private String dni; // Añadir DNI al DTO de entrada si se envía
    private String telefono;

    @NotBlank(message = "El método de pago es obligatorio") // Validar que el método de pago no sea nulo/vacío
    private String metodoPago; // Nuevo campo para el método de pago
}