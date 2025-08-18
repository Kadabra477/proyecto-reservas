package com.example.reservafutbol.Modelo;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    // **MODIFICACIÓN CLAVE**: Cambiamos de una lista a un mapa para almacenar URLs por resolución.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "complejo_fotos_resoluciones", joinColumns = @JoinColumn(name = "complejo_id"))
    @MapKeyColumn(name = "tipo_resolucion") // Clave: "thumbnail", "large", etc.
    @Column(name = "foto_url")
    private Map<String, String> fotoUrlsPorResolucion = new HashMap<>(); // Nuevo campo

    @Column(nullable = false)
    private LocalTime horarioApertura;
    @Column(nullable = false)
    private LocalTime horarioCierre;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "propietario_id")
    private User propietario;

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
