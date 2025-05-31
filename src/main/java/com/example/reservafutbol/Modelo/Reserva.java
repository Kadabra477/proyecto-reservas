package com.example.reservafutbol.Modelo;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List; // Mantén si jugadores puede ser List
import java.util.Set; // Importa Set

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
    private String estado = "pendiente";

    private String metodoPago;
    private Date fechaPago;
    private String mercadoPagoPaymentId;

    // Información Opcional de Equipos
    // MODIFICADO: Mantén List si el orden es crucial, pero Set resuelve el problema de fetch
    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> jugadores; // Si el orden importa, déjalo como List

    // CAMBIO CLAVE: Cambiar List a Set para equipo1 y equipo2 para evitar MultipleBagFetchException
    @ElementCollection(fetch = FetchType.EAGER)
    private Set<String> equipo1; // ¡CAMBIO AQUÍ!

    @ElementCollection(fetch = FetchType.EAGER)
    private Set<String> equipo2; // ¡CAMBIO AQUÍ!

    @PreUpdate
    @PrePersist
    public void actualizarEstadoGeneral() {
        if (Boolean.TRUE.equals(this.pagada)) {
            this.estado = "pagada";
        } else if (Boolean.TRUE.equals(this.confirmada)) {
            this.estado = "confirmada";
        } else {
            this.estado = "pendiente";
        }
    }
}