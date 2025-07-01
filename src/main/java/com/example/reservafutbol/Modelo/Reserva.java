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

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complejo_id", referencedColumnName = "id", nullable = false)
    private Complejo complejo;

    @Column(name = "complejo_nombre") // Esto se usaba para desnormalizar, asegurar que se actualice
    private String complejoNombre;

    @Column(name = "tipo_cancha_reservada", nullable = false)
    private String tipoCanchaReservada;

    @Column(name = "nombre_cancha_asignada")
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
    private String estado = "pendiente";

    private String metodoPago;
    private LocalDateTime fechaPago;
    private String mercadoPagoPaymentId;

    @PrePersist
    @PreUpdate
    public void updateComplejoNombreAndEstado() { // Renombrado para ser más descriptivo
        // Asegura que complejoNombre se guarde/actualice al persistir/actualizar la reserva
        if (this.complejo != null) {
            this.complejoNombre = this.complejo.getNombre();
        }

        // Lógica para actualizar el estado basada en 'pagada' y 'metodoPago'
        // Prioridad: Pagada > Rechazada/Cancelada > Pendiente Efe > Pendiente MP > Pendiente General
        if (Boolean.TRUE.equals(this.pagada)) {
            this.estado = "pagada";
        } else if ("rechazada_pago_mp".equalsIgnoreCase(this.estado)) {
            // Mantener estado de rechazada_pago_mp si ya está así
            this.estado = "rechazada_pago_mp";
        } else if ("cancelada".equalsIgnoreCase(this.estado)) {
            // Mantener estado de cancelada si ya está así
            this.estado = "cancelada";
        } else if ("efectivo".equalsIgnoreCase(this.metodoPago)) {
            this.estado = "pendiente_pago_efectivo";
        } else if ("mercadopago".equalsIgnoreCase(this.metodoPago)) {
            this.estado = "pendiente_pago_mp";
        } else {
            // Si no hay método de pago o es desconocido, o si el estado es null/vacío, se pone a 'pendiente'
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