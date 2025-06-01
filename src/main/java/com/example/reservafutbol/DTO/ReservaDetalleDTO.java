package com.example.reservafutbol.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReservaDetalleDTO {
    private Long id;
    private String userEmail;
    private Long canchaId;
    private String canchaNombre;
    private String canchaUbicacion;
    private String cliente;
    private String telefono;
    private LocalDateTime fechaHora;
    private Boolean confirmada;
    private BigDecimal precio;
    private Boolean pagada;
    private String estado;
    private String metodoPago; // Nuevo campo
    private String mercadoPagoPaymentId;
    private List<String> jugadores;
    private Set<String> equipo1;
    private Set<String> equipo2;

    public ReservaDetalleDTO(com.example.reservafutbol.Modelo.Reserva reserva) {
        this.id = reserva.getId();
        this.userEmail = reserva.getUserEmail();
        this.canchaId = reserva.getCancha() != null ? reserva.getCancha().getId() : null;
        this.canchaNombre = reserva.getCancha() != null ? reserva.getCancha().getNombre() : "N/A";
        this.canchaUbicacion = reserva.getCancha() != null ? reserva.getCancha().getUbicacion() : "N/A";
        this.cliente = reserva.getCliente();
        this.telefono = reserva.getTelefono();
        this.fechaHora = reserva.getFechaHora();
        this.confirmada = reserva.getConfirmada();
        this.precio = reserva.getPrecio();
        this.pagada = reserva.getPagada();
        this.estado = reserva.getEstado();
        this.metodoPago = reserva.getMetodoPago(); // Asignar el método de pago
        this.mercadoPagoPaymentId = reserva.getMercadoPagoPaymentId();
        this.jugadores = reserva.getJugadores();
        this.equipo1 = reserva.getEquipo1();
        this.equipo2 = reserva.getEquipo2();
    }
}