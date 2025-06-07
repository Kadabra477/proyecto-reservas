// backend/src/main/java/com/example/reservafutbol/payload/response/EstadisticasResponse.java
package com.example.reservafutbol.payload.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@AllArgsConstructor
public class EstadisticasResponse {
    private BigDecimal ingresosTotalesConfirmados;
    private Long totalReservasConfirmadas;
    private Long totalReservasPendientes;
    private Long totalReservasCanceladas; // Si manejas estado "cancelada"
    private Map<String, Long> reservasPorCancha; // Nombre Cancha -> Cantidad
    private Map<String, Long> horariosPico; // Hora (String) -> Cantidad de reservas
}