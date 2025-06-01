package com.example.reservafutbol.Modelo;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime; // Importado para el Servicio de Reservas
import java.time.LocalDate;  // Importado para el Servicio de Reservas
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

    // NUEVO CAMPO: Nombre de la cancha al momento de la reserva
    @Column(nullable = false)
    private String canchaNombre;

    @Column(nullable = false)
    private String cliente;

    private String telefono;

    @Column(nullable = false)
    private LocalDateTime fechaHora;

    // Se separan fecha y hora para el DTO y cálculos de estadísticas más claros.
    // Aunque fechaHora ya existe, tener LocalTime y LocalDate puede ser útil
    // para métodos en el servicio. Tu ReservaServicio actual ya extrae la hora.
    // Si no los vas a persistir, no los añadas a la entidad, solo úsalos en DTOs y servicios.
    // private LocalDate fecha;
    // private LocalTime horaInicio;


    @Column(nullable = false)
    private Boolean confirmada = false;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio; // Ya es precioTotal, pero el nombre del campo es 'precio'

    private Boolean pagada;

    @Column(nullable = false)
    private String estado = "pendiente"; // Estados: "pendiente", "confirmada_efectivo", "pendiente_pago_mp", "pagada", "rechazada_pago_mp", "cancelada"

    private String metodoPago; // "efectivo", "mercadopago"
    private Date fechaPago; // Solo para pagos, puede que necesites un LocalDateTime aquí también
    private String mercadoPagoPaymentId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "reserva_jugadores", joinColumns = @JoinColumn(name = "reserva_id"))
    @Column(name = "jugador")
    private List<String> jugadores;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "reserva_equipo1", joinColumns = @JoinColumn(name = "reserva_id"))
    @Column(name = "jugador_equipo1")
    private Set<String> equipo1;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "reserva_equipo2", joinColumns = @JoinColumn(name = "reserva_id"))
    @Column(name = "jugador_equipo2")
    private Set<String> equipo2;

    // MODIFICADO: Lógica de actualización de estado general
    // Esta lógica se ejecuta antes de persistir o actualizar la entidad
    @PreUpdate
    @PrePersist
    public void actualizarEstadoGeneral() {
        if (Boolean.TRUE.equals(this.pagada)) {
            this.estado = "pagada";
        } else if ("efectivo".equalsIgnoreCase(this.metodoPago) && Boolean.TRUE.equals(this.confirmada)) {
            this.estado = "confirmada_efectivo"; // Confirmada pero no pagada aún (para efectivo)
        } else if ("mercadopago".equalsIgnoreCase(this.metodoPago) && !Boolean.TRUE.equals(this.pagada)) {
            // Si es Mercado Pago y no está pagada (o fue rechazada/pendiente),
            // solo actualiza a 'pendiente_pago_mp' si el estado actual es 'pendiente'
            // o si es la primera vez que se asigna MP como método de pago.
            // Si ya está en 'rechazada_pago_mp' no lo sobrescribas aquí.
            if (this.estado == null || "pendiente".equalsIgnoreCase(this.estado)) {
                this.estado = "pendiente_pago_mp";
            }
            // Si el estado ya fue actualizado por un webhook (ej. 'rechazada_pago_mp'), no sobrescribir
        } else if (Boolean.TRUE.equals(this.confirmada)) {
            this.estado = "confirmada"; // Para otros casos de confirmación que no son efectivo/mp
        } else {
            // Si ninguna de las condiciones anteriores se cumple, se mantiene como pendiente por defecto
            if (this.estado == null || this.estado.isBlank()) { // Asegura que tenga un valor inicial
                this.estado = "pendiente";
            }
        }
    }

    // Método getter para 'fecha' y 'horaInicio' si no los persistes pero los necesitas en el servicio de estadísticas
    // Estos son transient (no se mapean a la BD) pero útiles para los DTOs y lógica.
    @Transient
    public LocalDate getFecha() {
        return this.fechaHora != null ? this.fechaHora.toLocalDate() : null;
    }

    @Transient
    public LocalTime getHoraInicio() {
        return this.fechaHora != null ? this.fechaHora.toLocalTime() : null;
    }
}