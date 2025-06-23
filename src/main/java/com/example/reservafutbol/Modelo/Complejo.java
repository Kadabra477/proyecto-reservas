package com.example.reservafutbol.Modelo;

// import com.fasterxml.jackson.annotation.JsonIgnore; // ELIMINAR ESTA IMPORTACIÓN
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "complejos") // Nueva tabla para los complejos deportivos
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Complejo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre; // Ej: "El Alargue", "Canchas del Sol"

    private String descripcion;
    private String ubicacion;
    private String telefono;
    private String fotoUrl; // Para la foto principal del complejo

    @Column(nullable = false)
    private LocalTime horarioApertura; // Ej: 10:00
    @Column(nullable = false)
    private LocalTime horarioCierre;   // Ej: 00:00 (medianoche)

    // --- Asociación con el propietario (User) ---
    // @JsonIgnore // <--- ELIMINAR ESTA ANOTACIÓN
    @ManyToOne(fetch = FetchType.LAZY) // Mantenemos LAZY para optimización, pero lo haremos FETCH en el repo si es necesario
    @JoinColumn(name = "propietario_id") // Columna que almacenará el ID del propietario en la tabla 'complejos'
    private User propietario; // El usuario que es dueño/administrador de este complejo

    // --- Detalles de Canchas por Tipo dentro de este Complejo ---

    // Mapa para almacenar la cantidad de canchas por tipo (Ej: {"Fútbol 5": 6, "Pádel": 2})
    // @ElementCollection permite mapear Map<String, Integer> a una tabla auxiliar
    @ElementCollection(fetch = FetchType.EAGER) // Carga las colecciones con el complejo
    @CollectionTable(name = "complejo_cancha_counts", // Nombre de la tabla auxiliar para las cantidades
            joinColumns = @JoinColumn(name = "complejo_id"))
    @MapKeyColumn(name = "tipo_cancha") // Columna para la clave del Map (ej. "Fútbol 5")
    @Column(name = "cantidad") // Columna para el valor del Map (ej. 6)
    private Map<String, Integer> canchaCounts = new HashMap<>(); // Inicializa para evitar NullPointer

    // Mapa para almacenar el precio por hora por tipo de cancha (Ej: {"Fútbol 5": 35000.0})
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "complejo_cancha_prices",
            joinColumns = @JoinColumn(name = "complejo_id"))
    @MapKeyColumn(name = "tipo_cancha")
    @Column(name = "precio_por_hora")
    private Map<String, Double> canchaPrices = new HashMap<>();

    // Mapa para almacenar la superficie por tipo de cancha (Ej: {"Fútbol 5": "Césped Sintético"})
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "complejo_cancha_surfaces",
            joinColumns = @JoinColumn(name = "complejo_id"))
    @MapKeyColumn(name = "tipo_cancha")
    @Column(name = "superficie")
    private Map<String, String> canchaSurfaces = new HashMap<>();

    // Mapa para almacenar si tiene iluminación por tipo de cancha (Ej: {"Fútbol 5": true})
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "complejo_cancha_iluminacion",
            joinColumns = @JoinColumn(name = "complejo_id"))
    @MapKeyColumn(name = "tipo_cancha")
    @Column(name = "iluminacion")
    private Map<String, Boolean> canchaIluminacion = new HashMap<>();

    // Mapa para almacenar si tiene techo por tipo de cancha (Ej: {"Fútbol 5": true})
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "complejo_cancha_techo",
            joinColumns = @JoinColumn(name = "complejo_id"))
    @MapKeyColumn(name = "tipo_cancha")
    @Column(name = "techo")
    private Map<String, Boolean> canchaTecho = new HashMap<>();


    // Constructor con campos básicos para facilitar la creación
    public Complejo(String nombre, String ubicacion, String telefono, LocalTime horarioApertura, LocalTime horarioCierre) {
        this.nombre = nombre;
        this.ubicacion = ubicacion;
        this.telefono = telefono;
        this.horarioApertura = horarioApertura;
        this.horarioCierre = horarioCierre;
    }
}