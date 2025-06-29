package com.example.reservafutbol.Modelo;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "reservas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "preference_id")
    private String preferenceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private User usuario;

    @Column(nullable = false)
    private String userEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complejo_id", referencedColumnName = "id", nullable = false)
    private Complejo complejo;

    @Column(nullable = false)
    private String tipoCanchaReservada;

    @Column(nullable = true)
    private String nombreCanchaAsignada;

    private String cliente;

    private String dni;

    private String telefono;

    @Column(nullable = false)
    private LocalDateTime fechaHora;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    private Boolean pagada;

    @Column(nullable = false)
    private String estado = "pendiente"; // Estados

    private String metodoPago;
    private LocalDateTime fechaPago;
    private String mercadoPagoPaymentId;


    @PreUpdate
    @PrePersist
    public void actualizarEstadoGeneral() {
        if (Boolean.TRUE.equals(this.pagada)) {
            this.estado = "pagada";
        } else if ("efectivo".equalsIgnoreCase(this.metodoPago)) {
            if (!"rechazada_pago_mp".equalsIgnoreCase(this.estado) && !"cancelada".equalsIgnoreCase(this.estado)) {
                this.estado = "pendiente_pago_efectivo";
            }
        } else if ("mercadopago".equalsIgnoreCase(this.metodoPago) && !Boolean.TRUE.equals(this.pagada)) {
            if (!"rechazada_pago_mp".equalsIgnoreCase(this.estado) && !"cancelada".equalsIgnoreCase(this.estado)) {
                this.estado = "pendiente_pago_mp";
            }
        } else {
            if (this.estado == null || this.estado.isBlank() || this.estado.equals("pendiente")) {
                this.estado = "pendiente";
            }
        }
    }

    @Transient
    public LocalDate getFecha() {
        return this.fechaHora != null ? this.fechaHora.toLocalDate() : null;
    }

    @Transient
    public LocalTime getHoraInicio() {
        return this.fechaHora != null ? this.fechaHora.toLocalTime() : null;
    }
}