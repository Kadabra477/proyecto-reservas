package com.example.reservafutbol.DTO;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class ReservaDTO {

    // ANTES: @NotNull(message = "El ID de la cancha es obligatorio")
    // ANTES: private Long canchaId;
    // AHORA: Ya no se envía el ID de la cancha específica desde el frontend.

    @NotBlank(message = "El tipo de cancha es obligatorio") // NUEVO: Validar que el tipo de cancha no sea nulo/vacío
    private String tipoCancha; // NUEVO: Campo para el tipo de cancha (ej. "Fútbol 5", "Pádel")

    @NotNull(message = "La fecha es obligatoria")
    // Se elimina @Future aquí porque la validación se hará más robusta en el servicio para evitar conflictos en un mismo día.
    private LocalDate fecha;

    @NotNull(message = "La hora es obligatoria")
    private LocalTime hora;

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    private String apellido;

    @NotBlank(message = "El DNI es obligatorio") // Añadir NotBlank si se valida que no esté vacío
    private String dni;

    @NotBlank(message = "El teléfono es obligatorio")
    private String telefono;

    @NotBlank(message = "El método de pago es obligatorio")
    private String metodoPago;

    // Si necesitas manejar jugadores y equipos desde el DTO, asegúrate de que estén aquí.
    // Los mantengo comentados ya que no estaban en tu DTO de entrada original en la última petición,
    // pero sí en Reserva.java. Si se envían desde el frontend, deben estar aquí.
    // private List<String> jugadores;
    // private Set<String> equipo1;
    // private Set<String> equipo2;
}