package com.example.reservafutbol.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.example.reservafutbol.Modelo.Reserva; // Importa la entidad Reserva
import com.example.reservafutbol.Modelo.Complejo; // Importa la entidad Complejo

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

    // --- CAMBIOS CLAVE: INFORMACIÓN DEL COMPLEJO EN LUGAR DE CANCHA ID ---
    private Long complejoId; // ID del complejo al que pertenece la reserva
    private String complejoNombre; // Nombre del complejo
    private String complejoUbicacion; // Ubicación del complejo

    private String tipoCanchaReservada; // Nuevo: Tipo de cancha que se reservó
    private String nombreCanchaAsignada; // Nombre de la cancha asignada internamente por el sistema (ej. "Fútbol 5 - Instancia 3")

    private String cliente;
    private String telefono;
    private LocalDateTime fechaHora;
    // ELIMINADO: Ya no se usa el booleano 'confirmada' directamente en el DTO si el estado es la fuente de verdad
    // private Boolean confirmada;
    private BigDecimal precioTotal;
    private Boolean pagada;
    private String estado;
    private String metodoPago;
    private String mercadoPagoPaymentId;
    private List<String> jugadores;
    private Set<String> equipo1;
    private Set<String> equipo2;

    public ReservaDetalleDTO(Reserva reserva) {
        this.id = reserva.getId();
        this.userEmail = reserva.getUserEmail();

        // Cargar información del Complejo
        this.complejoId = reserva.getComplejo() != null ? reserva.getComplejo().getId() : null;
        this.complejoNombre = reserva.getComplejo() != null ? reserva.getComplejo().getNombre() : "N/A";
        this.complejoUbicacion = reserva.getComplejo() != null ? reserva.getComplejo().getUbicacion() : "N/A";

        this.tipoCanchaReservada = reserva.getTipoCanchaReservada();
        this.nombreCanchaAsignada = reserva.getNombreCanchaAsignada(); // Se asigna el nombre interno

        this.cliente = reserva.getCliente();
        this.telefono = reserva.getTelefono();
        this.fechaHora = reserva.getFechaHora();
        // this.confirmada = reserva.getConfirmada(); // ELIMINADO
        this.precioTotal = reserva.getPrecio();
        this.pagada = reserva.getPagada();
        this.estado = reserva.getEstado();
        this.metodoPago = reserva.getMetodoPago();
        this.mercadoPagoPaymentId = reserva.getMercadoPagoPaymentId();
        this.jugadores = reserva.getJugadores();
        this.equipo1 = reserva.getEquipo1();
        this.equipo2 = reserva.getEquipo2();
    }
}