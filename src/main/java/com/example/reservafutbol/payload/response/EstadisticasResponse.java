// backend/src/main/java/com/example/reservafutbol/payload/response/EstadisticasResponse.java
package com.example.reservafutbol.payload.response;

import java.math.BigDecimal;
import java.util.Map;

public class EstadisticasResponse {
    private BigDecimal ingresosTotalesConfirmados;
    private Long totalReservasConfirmadas;
    private Long totalReservasPendientes;
    private Long totalReservasCanceladas; // Si manejas estado "cancelada"
    private Map<String, Long> reservasPorCancha; // Nombre Cancha -> Cantidad
    private Map<String, Long> horariosPico; // Hora (String) -> Cantidad de reservas

    // Constructor completo
    public EstadisticasResponse(BigDecimal ingresosTotalesConfirmados, Long totalReservasConfirmadas,
                                Long totalReservasPendientes, Long totalReservasCanceladas,
                                Map<String, Long> reservasPorCancha, Map<String, Long> horariosPico) {
        this.ingresosTotalesConfirmados = ingresosTotalesConfirmados;
        this.totalReservasConfirmadas = totalReservasConfirmadas;
        this.totalReservasPendientes = totalReservasPendientes;
        this.totalReservasCanceladas = totalReservasCanceladas;
        this.reservasPorCancha = reservasPorCancha;
        this.horariosPico = horariosPico;
    }

    // --- Getters y Setters ---
    public BigDecimal getIngresosTotalesConfirmados() {
        return ingresosTotalesConfirmados;
    }

    public void setIngresosTotalesConfirmados(BigDecimal ingresosTotalesConfirmados) {
        this.ingresosTotalesConfirmados = ingresosTotalesConfirmados;
    }

    public Long getTotalReservasConfirmadas() {
        return totalReservasConfirmadas;
    }

    public void setTotalReservasConfirmadas(Long totalReservasConfirmadas) {
        this.totalReservasConfirmadas = totalReservasConfirmadas;
    }

    public Long getTotalReservasPendientes() {
        return totalReservasPendientes;
    }

    public void setTotalReservasPendientes(Long totalReservasPendientes) {
        this.totalReservasPendientes = totalReservasPendientes;
    }

    public Long getTotalReservasCanceladas() {
        return totalReservasCanceladas;
    }

    public void setTotalReservasCanceladas(Long totalReservasCanceladas) {
        this.totalReservasCanceladas = totalReservasCanceladas;
    }

    public Map<String, Long> getReservasPorCancha() {
        return reservasPorCancha;
    }

    public void setReservasPorCancha(Map<String, Long> reservasPorCancha) {
        this.reservasPorCancha = reservasPorCancha;
    }

    public Map<String, Long> getHorariosPico() {
        return horariosPico;
    }

    public void setHorariosPico(Map<String, Long> horariosPico) {
        this.horariosPico = horariosPico;
    }
}