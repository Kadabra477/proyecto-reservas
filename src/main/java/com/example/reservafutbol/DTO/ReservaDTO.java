package com.example.reservafutbol.DTO;

import jakarta.validation.constraints.Future; // Puede que ya no necesitemos @Future si validamos en servicio
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data; // Asegúrate de importar Lombok Data

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List; // Necesario si manejas jugadores/equipos
import java.util.Set; // Necesario si manejas jugadores/equipos

@Data // Proporciona getters, setters, equals, hashCode, toString
public class ReservaDTO {

    @NotNull(message = "El ID del complejo es obligatorio")
    private Long complejoId; // NUEVO: ID del Complejo al que pertenece la reserva

    @NotBlank(message = "El tipo de cancha es obligatorio")
    private String tipoCancha; // Tipo de cancha que el usuario quiere reservar (ej. "Fútbol 5")

    @NotNull(message = "La fecha es obligatoria")
    // @Future(message = "La fecha debe ser en el futuro") // La validación detallada se hará en el Servicio
    private LocalDate fecha;

    @NotNull(message = "La hora es obligatoria")
    private LocalTime hora;

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    private String apellido;

    @NotBlank(message = "El DNI es obligatorio")
    private String dni;

    @NotBlank(message = "El teléfono es obligatorio")
    private String telefono;

    @NotBlank(message = "El método de pago es obligatorio")
    private String metodoPago;

    // Si la reserva incluye lista de jugadores o equipos, deben estar aquí en el DTO
    // private List<String> jugadores;
    // private Set<String> equipo1;
    // private Set<String> equipo2;
}