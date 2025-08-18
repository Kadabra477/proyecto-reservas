package com.example.reservafutbol.payload.response;

import com.example.reservafutbol.Modelo.Complejo;
import lombok.Data; // Importado para @Data
import lombok.NoArgsConstructor; // Importado para el constructor sin argumentos
import lombok.AllArgsConstructor; // Importado para el constructor con todos los argumentos

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors; // Se mantiene por si se usa en otros métodos, aunque no en el constructor directo

@Data // Genera getters, setters, toString, equals, hashCode
@NoArgsConstructor // Genera un constructor sin argumentos
@AllArgsConstructor // Genera un constructor con todos los argumentos
public class ComplejoResponseDTO {
    private Long id;
    private String nombre;
    private String descripcion;
    private String ubicacion;
    private String telefono;
    // MODIFICADO: Ahora es una lista de URLs, para simplificar el consumo en el frontend
    private List<String> fotoUrls;
    private LocalTime horarioApertura;
    private LocalTime horarioCierre;
    private String propietarioUsername;
    private Map<String, Integer> canchaCounts;
    private Map<String, Double> canchaPrices;
    private Map<String, String> canchaSurfaces;
    private Map<String, Boolean> canchaIluminacion;
    private Map<String, Boolean> canchaTecho;

    // Constructor para mapear desde Complejo a ComplejoResponseDTO
    public ComplejoResponseDTO(Complejo complejo) {
        this.id = complejo.getId();
        this.nombre = complejo.getNombre();
        this.descripcion = complejo.getDescripcion();
        this.ubicacion = complejo.getUbicacion();
        this.telefono = complejo.getTelefono();

        // MODIFICADO: Mapear el mapa de URLs por resolución a una lista simple
        // Si el complejo tiene fotoUrlsPorResolucion y contiene una miniatura,
        // la añadimos a la lista. De lo contrario, la lista estará vacía.
        if (complejo.getFotoUrlsPorResolucion() != null && complejo.getFotoUrlsPorResolucion().containsKey("thumbnail")) {
            this.fotoUrls = Collections.singletonList(complejo.getFotoUrlsPorResolucion().get("thumbnail"));
        } else {
            this.fotoUrls = Collections.emptyList();
        }

        this.horarioApertura = complejo.getHorarioApertura();
        this.horarioCierre = complejo.getHorarioCierre();
        this.propietarioUsername = complejo.getPropietario() != null ? complejo.getPropietario().getUsername() : null;
        this.canchaCounts = complejo.getCanchaCounts();
        this.canchaPrices = complejo.getCanchaPrices();
        this.canchaSurfaces = complejo.getCanchaSurfaces();
        this.canchaIluminacion = complejo.getCanchaIluminacion();
        this.canchaTecho = complejo.getCanchaTecho();
        // NOTA: No se incluye el propietario aquí para la vista pública (solo el username)
    }
    // El método getFotoUrls() manual ya no es necesario si se usa @Data,
    // ya que Lombok lo generará automáticamente para el campo 'fotoUrls'.
}
