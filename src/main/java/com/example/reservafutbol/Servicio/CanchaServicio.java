package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Modelo.Cancha;
import com.example.reservafutbol.Repositorio.CanchaRepositorio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CanchaServicio {

    @Autowired
    private CanchaRepositorio canchaRepositorio;

    // Obtener todas las canchas
    public List<Cancha> listarCanchas() {
        return canchaRepositorio.findAll();
    }

    // Crear una nueva cancha (MODIFICADO)
    public Cancha crearCancha(Cancha cancha) {
        if (cancha.getNombre() == null || cancha.getNombre().isEmpty()) {
            throw new IllegalArgumentException("El nombre de la cancha no puede estar vacío");
        }
        // NUEVOS CAMPOS: Validar o asignar valores por defecto
        if (cancha.getTipoCancha() == null || cancha.getTipoCancha().isEmpty()) {
            throw new IllegalArgumentException("El tipo de cancha es obligatorio.");
        }
        if (cancha.getSuperficie() == null || cancha.getSuperficie().isEmpty()) {
            throw new IllegalArgumentException("La superficie de la cancha es obligatoria.");
        }
        // Los booleanos iluminacion y techo se manejan por defecto si no se envían.
        // Asegúrate que si el frontend envía `null`, lo manejes aquí si deben ser `false`.
        if (cancha.getIluminacion() == null) {
            cancha.setIluminacion(false);
        }
        if (cancha.getTecho() == null) {
            cancha.setTecho(false);
        }
        if (cancha.getDisponible() == null) {
            cancha.setDisponible(true); // Asume disponible por defecto al crear
        }

        return canchaRepositorio.save(cancha);
    }

    public Optional<Cancha> buscarPorId(Long id) {
        return canchaRepositorio.findById(id);
    }

    // Actualizar una cancha existente (MODIFICADO)
    public Cancha actualizarCancha(Long id, Cancha canchaDetails) {
        Cancha canchaExistente = canchaRepositorio.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cancha no encontrada con ID: " + id));

        // Validaciones de campos obligatorios
        if (canchaDetails.getNombre() == null || canchaDetails.getNombre().isEmpty()) {
            throw new IllegalArgumentException("El nombre de la cancha no puede estar vacío");
        }
        if (canchaDetails.getTipoCancha() == null || canchaDetails.getTipoCancha().isEmpty()) {
            throw new IllegalArgumentException("El tipo de cancha es obligatorio.");
        }
        if (canchaDetails.getSuperficie() == null || canchaDetails.getSuperficie().isEmpty()) {
            throw new IllegalArgumentException("La superficie de la cancha es obligatoria.");
        }

        // Actualizar todos los campos
        canchaExistente.setNombre(canchaDetails.getNombre());
        canchaExistente.setDescripcion(canchaDetails.getDescripcion());
        canchaExistente.setFotoUrl(canchaDetails.getFotoUrl());
        canchaExistente.setUbicacionMaps(canchaDetails.getUbicacionMaps());
        canchaExistente.setTelefono(canchaDetails.getTelefono());
        canchaExistente.setUbicacion(canchaDetails.getUbicacion());
        canchaExistente.setPrecioPorHora(canchaDetails.getPrecioPorHora());
        canchaExistente.setDisponible(canchaDetails.getDisponible());
        // Nuevos campos
        canchaExistente.setTipoCancha(canchaDetails.getTipoCancha());
        canchaExistente.setSuperficie(canchaDetails.getSuperficie());
        canchaExistente.setIluminacion(canchaDetails.getIluminacion() != null ? canchaDetails.getIluminacion() : false);
        canchaExistente.setTecho(canchaDetails.getTecho() != null ? canchaDetails.getTecho() : false);

        return canchaRepositorio.save(canchaExistente);
    }

    public boolean eliminarCancha(Long id) {
        if (canchaRepositorio.existsById(id)) {
            canchaRepositorio.deleteById(id);
            return true;
        } else {
            throw new IllegalArgumentException("La cancha no existe");
        }
    }
}