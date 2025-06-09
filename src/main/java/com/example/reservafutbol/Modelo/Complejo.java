package com.example.reservafutbol.Modelo;

import com.fasterxml.jackson.annotation.JsonIgnore; // Importar JsonIgnore
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "complejos")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Complejo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    private String descripcion;
    private String ubicacion;
    private String telefono;
    private String fotoUrl;

    @Column(nullable = false)
    private LocalTime horarioApertura;
    @Column(nullable = false)
    private LocalTime horarioCierre;

    // --- Asociación con el propietario (User) ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "propietario_id")
    @JsonIgnore // <--- AÑADIR ESTA ANOTACIÓN
    private User propietario;

    // --- Detalles de Canchas por Tipo dentro de este Complejo ---
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "complejo_cancha_counts", joinColumns = @JoinColumn(name = "complejo_id"))
    @MapKeyColumn(name = "tipo_cancha")
    @Column(name = "cantidad")
    private Map<String, Integer> canchaCounts = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "complejo_cancha_prices", joinColumns = @JoinColumn(name = "complejo_id"))
    @MapKeyColumn(name = "tipo_cancha")
    @Column(name = "precio_por_hora")
    private Map<String, Double> canchaPrices = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "complejo_cancha_surfaces", joinColumns = @JoinColumn(name = "complejo_id"))
    @MapKeyColumn(name = "tipo_cancha")
    @Column(name = "superficie")
    private Map<String, String> canchaSurfaces = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "complejo_cancha_iluminacion", joinColumns = @JoinColumn(name = "complejo_id"))
    @MapKeyColumn(name = "tipo_cancha")
    @Column(name = "iluminacion")
    private Map<String, Boolean> canchaIluminacion = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "complejo_cancha_techo", joinColumns = @JoinColumn(name = "complejo_id"))
    @MapKeyColumn(name = "tipo_cancha")
    @Column(name = "techo")
    private Map<String, Boolean> canchaTecho = new HashMap<>();

    public Complejo(String nombre, String ubicacion, String telefono, LocalTime horarioApertura, LocalTime horarioCierre) {
        this.nombre = nombre;
        this.ubicacion = ubicacion;
        this.telefono = telefono;
        this.horarioApertura = horarioApertura;
        this.horarioCierre = horarioCierre;
    }
}