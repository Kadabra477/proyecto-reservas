package com.example.reservafutbol.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date; // Para fechaPago
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReservaDetalleDTO { // ¡Nuevo nombre de DTO!
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
    private String metodoPago;
    private String mercadoPagoPaymentId;
    private List<String> jugadores; // Para incluir los jugadores
    private List<String> equipo1; // Para incluir equipo1
    private List<String> equipo2; // Para incluir equipo2
    // Puedes añadir más campos de la cancha si los necesitas
    // private String canchaFotoUrl;
    // private Double canchaPrecioPorHora;


    // Constructor para mapear desde la entidad Reserva
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
        this.metodoPago = reserva.getMetodoPago();
        this.mercadoPagoPaymentId = reserva.getMercadoPagoPaymentId();
        this.jugadores = reserva.getJugadores();
        this.equipo1 = reserva.getEquipo1();
        this.equipo2 = reserva.getEquipo2();
        // this.canchaFotoUrl = reserva.getCancha() != null ? reserva.getCancha().getFotoUrl() : null;
        // this.canchaPrecioPorHora = reserva.getCancha() != null ? reserva.getCancha().getPrecioPorHora() : null;
    }
}