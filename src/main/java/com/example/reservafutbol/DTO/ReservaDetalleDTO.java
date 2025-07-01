package com.example.reservafutbol.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.example.reservafutbol.Modelo.Reserva;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservaDetalleDTO {
    private Long id;
    private String userEmail;

    // --- INFORMACIÓN DEL COMPLEJO ---
    private Long complejoId;
    private String complejoNombre;
    private String complejoUbicacion;

    private String tipoCanchaReservada;
    private String nombreCanchaAsignada;

    private String cliente;
    private String dni;
    private String telefono;
    private LocalDateTime fechaHora;
    private BigDecimal precioTotal;
    private Boolean pagada;
    private String estado;
    private String metodoPago;
    private String mercadoPagoPaymentId;

    // Constructor personalizado para mapear desde la entidad Reserva
    public ReservaDetalleDTO(Reserva reserva) {
        this.id = reserva.getId();
        this.userEmail = reserva.getUserEmail();

        // Cargar información del Complejo
        this.complejoId = reserva.getComplejo() != null ? reserva.getComplejo().getId() : null;
        this.complejoNombre = reserva.getComplejo() != null ? reserva.getComplejo().getNombre() : "N/A";
        this.complejoUbicacion = reserva.getComplejo() != null ? reserva.getComplejo().getUbicacion() : "N/A";

        this.tipoCanchaReservada = reserva.getTipoCanchaReservada();
        this.nombreCanchaAsignada = reserva.getNombreCanchaAsignada();

        this.cliente = reserva.getCliente();
        this.dni = reserva.getDni();
        this.telefono = reserva.getTelefono();
        this.fechaHora = reserva.getFechaHora();
        this.precioTotal = reserva.getPrecio(); // Asumo que el precio se mapea correctamente aquí
        this.pagada = reserva.getPagada();
        this.estado = reserva.getEstado();
        this.metodoPago = reserva.getMetodoPago();
        this.mercadoPagoPaymentId = reserva.getMercadoPagoPaymentId();
    }
}