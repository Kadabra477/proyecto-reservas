package com.example.reservafutbol.payload.response;

import com.example.reservafutbol.Modelo.Complejo;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComplejoResponseDTO {
    private Long id;
    private String nombre;
    private String descripcion;
    private String ubicacion;
    private String telefono;
    private String portadaUrl;
    private List<String> carruselUrls;
    private LocalTime horarioApertura;
    private LocalTime horarioCierre;
    private String propietarioUsername;
    private Map<String, Integer> canchaCounts;
    private Map<String, Double> canchaPrices;
    private Map<String, String> canchaSurfaces;
    private Map<String, Boolean> canchaIluminacion;
    private Map<String, Boolean> canchaTecho;

    public ComplejoResponseDTO(Complejo complejo) {
        this.id = complejo.getId();
        this.nombre = complejo.getNombre();
        this.descripcion = complejo.getDescripcion();
        this.ubicacion = complejo.getUbicacion();
        this.telefono = complejo.getTelefono();

        if (complejo.getFotoUrlsPorResolucion() != null) {
            this.portadaUrl = complejo.getFotoUrlsPorResolucion().get("thumbnail");
        } else {
            this.portadaUrl = null;
        }

        if (complejo.getFotoUrlsPorResolucion() != null) {
            this.carruselUrls = complejo.getFotoUrlsPorResolucion().entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("carousel_"))
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());
        } else {
            this.carruselUrls = Collections.emptyList();
        }

        this.horarioApertura = complejo.getHorarioApertura();
        this.horarioCierre = complejo.getHorarioCierre();
        this.propietarioUsername = complejo.getPropietario() != null ? complejo.getPropietario().getUsername() : null;
        this.canchaCounts = complejo.getCanchaCounts();
        this.canchaPrices = complejo.getCanchaPrices();
        this.canchaSurfaces = complejo.getCanchaSurfaces();
        this.canchaIluminacion = complejo.getCanchaIluminacion();
        this.canchaTecho = complejo.getCanchaTecho();
    }
}