// backend/src/main/java/com/example/reservafutbol/payload/response/ComplejoResponseDTO.java
package com.example.reservafutbol.payload.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComplejoResponseDTO {
    private Long id;
    private String nombre;
    private String descripcion;
    private String ubicacion;
    private String telefono;
    private String fotoUrl;
    private LocalTime horarioApertura;
    private LocalTime horarioCierre;
    private Map<String, Integer> canchaCounts;
    private Map<String, Double> canchaPrices;
    private Map<String, String> canchaSurfaces;
    private Map<String, Boolean> canchaIluminacion;
    private Map<String, Boolean> canchaTecho;

    // Puedes añadir un constructor para mapear desde Complejo a ComplejoResponseDTO
    // O usar un mapper (ej. ModelMapper, MapStruct)
    public ComplejoResponseDTO(com.example.reservafutbol.Modelo.Complejo complejo) {
        this.id = complejo.getId();
        this.nombre = complejo.getNombre();
        this.descripcion = complejo.getDescripcion();
        this.ubicacion = complejo.getUbicacion();
        this.telefono = complejo.getTelefono();
        this.fotoUrl = complejo.getFotoUrl();
        this.horarioApertura = complejo.getHorarioApertura();
        this.horarioCierre = complejo.getHorarioCierre();
        this.canchaCounts = complejo.getCanchaCounts();
        this.canchaPrices = complejo.getCanchaPrices();
        this.canchaSurfaces = complejo.getCanchaSurfaces();
        this.canchaIluminacion = complejo.getCanchaIluminacion();
        this.canchaTecho = complejo.getCanchaTecho();
        // NOTA: No se incluye el propietario aquí para la vista pública
    }
}