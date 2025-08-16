package com.example.reservafutbol.payload.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalTime;
import java.util.List; // Importamos java.util.List
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
    private List<String> fotoUrls; // Modificado: Ahora es una lista
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
        this.fotoUrls = complejo.getFotoUrls(); // Modificado: Llamamos al nuevo método
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