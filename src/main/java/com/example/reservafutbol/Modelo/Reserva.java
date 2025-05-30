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

@Entity
@Table(name = "reservas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Reserva {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "preference_id")
    private String preferenceId;


    // Relación con el usuario que realiza la reserva (más robusto)
    @ManyToOne(fetch = FetchType.LAZY) // LAZY para no cargar siempre el usuario
    @JoinColumn(name = "usuario_id") // Asegúrate que esta columna exista en tu BD
    private User usuario;

    // Email denormalizado (útil si no siempre tienes el objeto User a mano)
    @Column(nullable = false)
    private String userEmail;

    @ManyToOne(fetch = FetchType.LAZY) // LAZY para no cargar siempre la cancha
    @JoinColumn(name = "cancha_id", referencedColumnName = "id", nullable = false)
    private Cancha cancha;

    @Column(nullable = false)
    private String cliente; // Nombre de quien figura en la reserva (puede ser distinto al userEmail)

    private String telefono;

    @Column(nullable = false)
    private LocalDateTime fechaHora; // Fecha/Hora exacta de inicio

    // Estado de confirmación (ej. admin confirma que los datos son válidos)
    @Column(nullable = false)
    private Boolean confirmada = false;

    // Precio total de esta reserva específica (copiado de la cancha o calculado)
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    private Boolean pagada; // <-- Inicializado a false, reemplaza pagoRealizado

    // Estado general (se deriva de confirmada y pagada)
    @Column(nullable = false)
    private String estado = "pendiente"; // "pendiente", "confirmada", "pagada", "cancelada"?

    private String metodoPago; // "efectivo", "MercadoPago"
    private Date fechaPago;
    private String mercadoPagoPaymentId; // Para guardar referencia del pago en MP

    // Información Opcional de Equipos
    // MODIFICADO: FetchType a EAGER para resolver LazyInitializationException
    @ElementCollection(fetch = FetchType.EAGER) // <-- ¡CAMBIO AQUÍ!
    private List<String> jugadores;

    // MODIFICADO: FetchType a EAGER para resolver LazyInitializationException
    @ElementCollection(fetch = FetchType.EAGER) // <-- ¡CAMBIO AQUÍ!
    private List<String> equipo1;

    // MODIFICADO: FetchType a EAGER para resolver LazyInitializationException
    @ElementCollection(fetch = FetchType.EAGER) // <-- ¡CAMBIO AQUÍ!
    private List<String> equipo2;

    // --- Métodos Helper para actualizar estado ---
    @PreUpdate // Se ejecuta antes de actualizar en BD
    @PrePersist // Se ejecuta antes de guardar por primera vez
    public void actualizarEstadoGeneral() {
        if (Boolean.TRUE.equals(this.pagada)) {
            this.estado = "pagada";
        } else if (Boolean.TRUE.equals(this.confirmada)) {
            this.estado = "confirmada"; // Confirmada pero no pagada aún
        } else {
            this.estado = "pendiente"; // Ni confirmada ni pagada
        }
    }
}