package com.example.reservafutbol.Modelo;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "reservas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "preference_id")
    private String preferenceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private User usuario;

    @Column(nullable = false)
    private String userEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancha_id", referencedColumnName = "id", nullable = false)
    private Cancha cancha;

    @Column(nullable = false)
    private String cliente;

    private String telefono;

    @Column(nullable = false)
    private LocalDateTime fechaHora;

    @Column(nullable = false)
    private Boolean confirmada = false;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    private Boolean pagada;

    @Column(nullable = false)
    private String estado = "pendiente"; // Estados: "pendiente", "confirmada_efectivo", "pendiente_pago", "pagada", "rechazada_pago_mp", "cancelada"

    private String metodoPago; // "efectivo", "mercadopago"
    private Date fechaPago;
    private String mercadoPagoPaymentId;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> jugadores;

    @ElementCollection(fetch = FetchType.EAGER)
    private Set<String> equipo1;

    @ElementCollection(fetch = FetchType.EAGER)
    private Set<String> equipo2;

    // MODIFICADO: Lógica de actualización de estado general
    @PreUpdate
    @PrePersist
    public void actualizarEstadoGeneral() {
        if (Boolean.TRUE.equals(this.pagada)) {
            this.estado = "pagada";
        } else if ("efectivo".equalsIgnoreCase(this.metodoPago) && Boolean.TRUE.equals(this.confirmada)) {
            this.estado = "confirmada_efectivo"; // Confirmada pero no pagada aún (para efectivo)
        } else if ("mercadopago".equalsIgnoreCase(this.metodoPago) && !Boolean.TRUE.equals(this.pagada)) {
            // Si es Mercado Pago y no está pagada (o fue rechazada/pendiente), el estado lo maneja el webhook de MP
            if (this.estado == null || this.estado.equals("pendiente")) { // Solo si no ha sido actualizado por MP
                this.estado = "pendiente_pago";
            }
        } else if (Boolean.TRUE.equals(this.confirmada)) {
            this.estado = "confirmada"; // Para otros casos de confirmación
        } else {
            this.estado = "pendiente";
        }
    }
}