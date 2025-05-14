package com.example.reservafutbol.Modelo;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "canchas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Cancha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String descripcion;
    private String fotoUrl;
    private String ubicacionMaps;
    private String telefono;
    private String ubicacion;
    private Double precioPorHora;
    private Boolean disponible;
}
