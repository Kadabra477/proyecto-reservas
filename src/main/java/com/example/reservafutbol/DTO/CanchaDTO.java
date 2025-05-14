package com.example.reservafutbol.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO simple para representar la información básica de una Cancha
 * al ser incluida dentro de otros DTOs (ej. ReservaDetalleDTO).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CanchaDTO {
    private Long id;
    private String nombre;
    // Puedes añadir aquí otros campos simples si los necesitas, ej: tipo, ubicacion
    // private String tipo;
    // private String ubicacion;
}