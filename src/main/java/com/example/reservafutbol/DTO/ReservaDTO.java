package com.example.reservafutbol.DTO;

import com.example.reservafutbol.Modelo.EstadoReserva; // Asegúrate de que esta enum existe
import com.example.reservafutbol.Modelo.Reserva; // Importar la entidad Reserva

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservaDTO {

    private Long id; // Puede ser nulo para creación

    @NotNull(message = "El ID del complejo es obligatorio.")
    private Long complejoId;

    @NotBlank(message = "El tipo de cancha es obligatorio.")
    private String tipoCancha;

    @NotBlank(message = "El nombre del cliente es obligatorio.")
    private String nombre; // Campo de nombre individual

    @NotBlank(message = "El apellido del cliente es obligatorio.")
    private String apellido; // Campo de apellido individual

    @NotNull(message = "El DNI del cliente es obligatorio.")
    // Se usa Integer porque el frontend envía un número, aunque el modelo de entidad lo tenga como String
    // Esto es para la validación y el parseo inicial
    private Integer dni;

    @NotBlank(message = "El email del cliente es obligatorio.")
    @Email(message = "Formato de correo electrónico inválido")
    private String email; // Mapea a userEmail en la entidad

    @NotBlank(message = "El teléfono del cliente es obligatorio.")
    @Pattern(regexp = "^[0-9]{7,15}$", message = "El teléfono debe contener solo números y tener entre 7 y 15 dígitos.")
    private String telefono;

    @NotNull(message = "La fecha de la reserva es obligatoria.")
    private LocalDate fecha;

    @NotNull(message = "La hora de la reserva es obligatoria.")
    private LocalTime hora;

    @NotBlank(message = "El método de pago es obligatorio.")
    private String metodoPago;

    // Campos que el backend puede devolver, pero no se envían desde el frontend en la creación
    private String complejoNombre;
    private String nombreCanchaAsignada;
    private BigDecimal precioTotal;
    private EstadoReserva estado; // Usar el enum de estado
    private Boolean pagada;
    private String mercadoPagoPaymentId; // Para Mercado Pago

    // Constructor para mapear desde la entidad Reserva a DTO (se usa al devolver respuestas al frontend)
    public ReservaDTO(Reserva reserva) {
        this.id = reserva.getId();
        this.complejoId = reserva.getComplejo() != null ? reserva.getComplejo().getId() : null;
        this.tipoCancha = reserva.getTipoCanchaReservada();

        // Lógica para intentar dividir el campo 'cliente' en nombre y apellido al crear el DTO
        if (reserva.getCliente() != null && !reserva.getCliente().isBlank()) {
            String[] partesCliente = reserva.getCliente().trim().split("\\s+", 2); // Divide en máximo 2 partes
            this.nombre = partesCliente[0];
            if (partesCliente.length > 1) {
                this.apellido = partesCliente[1];
            } else {
                this.apellido = ""; // Si no hay apellido, se deja vacío
            }
        } else {
            this.nombre = "";
            this.apellido = "";
        }

        // DNI del modelo Reserva es String, aquí lo parseamos a Integer si es posible, o lo dejamos null
        this.dni = (reserva.getDni() != null && reserva.getDni().matches("\\d+")) ? Integer.parseInt(reserva.getDni()) : null;

        this.email = reserva.getUserEmail();
        this.telefono = reserva.getTelefono();
        this.fecha = reserva.getFechaHora() != null ? reserva.getFechaHora().toLocalDate() : null;
        this.hora = reserva.getFechaHora() != null ? reserva.getFechaHora().toLocalTime() : null;
        this.metodoPago = reserva.getMetodoPago();
        this.complejoNombre = reserva.getComplejo() != null ? reserva.getComplejo().getNombre() : null;
        this.nombreCanchaAsignada = reserva.getNombreCanchaAsignada();
        this.precioTotal = reserva.getPrecio();
        this.estado = EstadoReserva.fromString(reserva.getEstado()); // Asegúrate de que fromString existe en EstadoReserva
        this.pagada = reserva.getPagada();
        this.mercadoPagoPaymentId = reserva.getMercadoPagoPaymentId();
    }
}