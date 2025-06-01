package com.example.reservafutbol.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.example.reservafutbol.Modelo.Reserva; // Importa la entidad Reserva

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date; // Asegúrate de que Reserva usa Date o LocalDateTime consistentemente
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
    private String canchaNombre; // Campo directo de la entidad
    private String canchaUbicacion;
    private String cliente;
    private String telefono;
    private LocalDateTime fechaHora;
    private Boolean confirmada;
    private BigDecimal precioTotal; // Renombrado a precioTotal en DTO para mayor claridad
    private Boolean pagada;
    private String estado;
    private String metodoPago;
    private String mercadoPagoPaymentId;
    private List<String> jugadores;
    private Set<String> equipo1;
    private Set<String> equipo2;

    public ReservaDetalleDTO(Reserva reserva) { // Usa la entidad Reserva para el constructor
        this.id = reserva.getId();
        this.userEmail = reserva.getUserEmail();
        this.canchaId = reserva.getCancha() != null ? reserva.getCancha().getId() : null;
        this.canchaNombre = reserva.getCanchaNombre() != null ? reserva.getCanchaNombre() : (reserva.getCancha() != null ? reserva.getCancha().getNombre() : "N/A"); // Prioriza el campo directo
        this.canchaUbicacion = reserva.getCancha() != null ? reserva.getCancha().getUbicacion() : "N/A";
        this.cliente = reserva.getCliente();
        this.telefono = reserva.getTelefono();
        this.fechaHora = reserva.getFechaHora();
        this.confirmada = reserva.getConfirmada();
        this.precioTotal = reserva.getPrecio(); // Mapea el campo 'precio' de Reserva a 'precioTotal' del DTO
        this.pagada = reserva.getPagada();
        this.estado = reserva.getEstado();
        this.metodoPago = reserva.getMetodoPago();
        this.mercadoPagoPaymentId = reserva.getMercadoPagoPaymentId();
        this.jugadores = reserva.getJugadores();
        this.equipo1 = reserva.getEquipo1();
        this.equipo2 = reserva.getEquipo2();
    }
}