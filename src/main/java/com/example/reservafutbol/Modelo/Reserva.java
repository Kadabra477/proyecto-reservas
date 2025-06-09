package com.example.reservafutbol.Modelo;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder; // Importar Builder
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "reservas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder // Añadido @Builder aquí
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "preference_id")
    private String preferenceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false) // Una reserva debe tener un usuario
    private User usuario;

    @Column(nullable = false)
    private String userEmail;

    // --- CAMBIOS CLAVE: RELACIÓN CON COMPLEJO Y TIPO DE CANCHA ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complejo_id", referencedColumnName = "id", nullable = false)
    private Complejo complejo; // Ahora la reserva se asocia directamente a un Complejo

    @Column(nullable = false)
    private String tipoCanchaReservada; // Nuevo: El tipo de cancha que el usuario reservó (ej: "Fútbol 5", "Pádel")

    // Nombre de la cancha asignada internamente (ej: "Fútbol 5 - Instancia 3")
    // Se guarda como String para referencia, ya que no hay entidad Cancha.
    // Esto lo asignará el sistema al momento de la reserva.
    @Column(nullable = true) // Puede ser null si el complejo no numera sus instancias idénticas
    private String nombreCanchaAsignada;

    private String cliente; // Contendrá "Nombre Apellido"

    // Se mantiene como String para flexibilidad, aunque el DTO envíe Integer
    // La conversión se puede hacer en el controlador o servicio si se desea validar/guardar como numérico
    private String dni;

    private String telefono;

    @Column(nullable = false)
    private LocalDateTime fechaHora;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio; // Precio total de la reserva

    private Boolean pagada; // Indica si la reserva está pagada o no

    @Column(nullable = false)
    private String estado = "pendiente"; // Estados: "pendiente", "pendiente_pago_efectivo", "pendiente_pago_mp", "pagada", "rechazada_pago_mp", "cancelada"

    private String metodoPago;
    private LocalDateTime fechaPago; // Cambiado a LocalDateTime para consistencia
    private String mercadoPagoPaymentId; // ID de la transacción de Mercado Pago

    @ElementCollection(fetch = FetchType.EAGER) // Carga los jugadores con la reserva
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


    // Lógica que se ejecuta antes de persistir o actualizar la entidad
    @PreUpdate
    @PrePersist
    public void actualizarEstadoGeneral() {
        if (Boolean.TRUE.equals(this.pagada)) {
            this.estado = "pagada";
        } else if ("efectivo".equalsIgnoreCase(this.metodoPago)) {
            // Si es efectivo y no está pagada y el estado no es ya rechazado/cancelado
            if (!"rechazada_pago_mp".equalsIgnoreCase(this.estado) && !"cancelada".equalsIgnoreCase(this.estado)) {
                this.estado = "pendiente_pago_efectivo";
            }
        } else if ("mercadopago".equalsIgnoreCase(this.metodoPago) && !Boolean.TRUE.equals(this.pagada)) {
            // Si es MP y no está pagada y el estado no es ya rechazado/cancelado
            if (!"rechazada_pago_mp".equalsIgnoreCase(this.estado) && !"cancelada".equalsIgnoreCase(this.estado)) {
                this.estado = "pendiente_pago_mp";
            }
        } else {
            // Estado por defecto si no encaja con las anteriores, por ejemplo si se crea sin método de pago o se cancela.
            if (this.estado == null || this.estado.isBlank() || this.estado.equals("pendiente")) { // Mantener 'pendiente' si ya lo es
                this.estado = "pendiente";
            }
        }
    }

    // Métodos transient para acceder a la fecha y hora de inicio (útiles para DTOs o lógica)
    @Transient
    public LocalDate getFecha() {
        return this.fechaHora != null ? this.fechaHora.toLocalDate() : null;
    }

    @Transient
    public LocalTime getHoraInicio() {
        return this.fechaHora != null ? this.fechaHora.toLocalTime() : null;
    }
}