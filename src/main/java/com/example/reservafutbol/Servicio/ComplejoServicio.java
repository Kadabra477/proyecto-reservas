package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Modelo.Complejo;
import com.example.reservafutbol.Repositorio.ComplejoRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap; // Necesario para inicializar Mapas
import java.util.List;
import java.util.Optional;

@Service
public class ComplejoServicio {

    private static final Logger log = LoggerFactory.getLogger(ComplejoServicio.class);

    @Autowired
    private ComplejoRepositorio complejoRepositorio;

    @Transactional
    public Complejo crearComplejo(Complejo complejo) {
        log.info("Creando nuevo complejo: {}", complejo.getNombre());
        // Validaciones básicas para la creación de un complejo
        if (complejo.getNombre() == null || complejo.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del complejo es obligatorio.");
        }
        if (complejo.getHorarioApertura() == null || complejo.getHorarioCierre() == null) {
            throw new IllegalArgumentException("El horario de apertura y cierre del complejo es obligatorio.");
        }
        // Asegurar que los mapas de canchas se inicialicen si no vienen del frontend
        if (complejo.getCanchaCounts() == null) complejo.setCanchaCounts(new HashMap<>());
        if (complejo.getCanchaPrices() == null) complejo.setCanchaPrices(new HashMap<>());
        if (complejo.getCanchaSurfaces() == null) complejo.setCanchaSurfaces(new HashMap<>());
        if (complejo.getCanchaIluminacion() == null) complejo.setCanchaIluminacion(new HashMap<>());
        if (complejo.getCanchaTecho() == null) complejo.setCanchaTecho(new HashMap<>());

        return complejoRepositorio.save(complejo);
    }

    @Transactional(readOnly = true)
    public List<Complejo> listarTodosLosComplejos() {
        log.info("Listando todos los complejos.");
        return complejoRepositorio.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Complejo> buscarComplejoPorId(Long id) {
        log.info("Buscando complejo por ID: {}", id);
        return complejoRepositorio.findById(id);
    }

    @Transactional
    public Complejo actualizarComplejo(Long id, Complejo complejoDetails) {
        log.info("Actualizando complejo con ID: {}", id);
        Complejo complejoExistente = complejoRepositorio.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Complejo no encontrado con ID: " + id));

        // Actualizar campos principales
        complejoExistente.setNombre(complejoDetails.getNombre());
        complejoExistente.setDescripcion(complejoDetails.getDescripcion());
        complejoExistente.setUbicacion(complejoDetails.getUbicacion());
        complejoExistente.setTelefono(complejoDetails.getTelefono());
        complejoExistente.setFotoUrl(complejoDetails.getFotoUrl());
        complejoExistente.setHorarioApertura(complejoDetails.getHorarioApertura());
        complejoExistente.setHorarioCierre(complejoDetails.getHorarioCierre());

        // Actualizar los mapas de tipos de canchas y sus propiedades
        // Se utilizan operadores ternarios para asegurar que si viene null, se inicialice a un nuevo HashMap vacío
        complejoExistente.setCanchaCounts(complejoDetails.getCanchaCounts() != null ? complejoDetails.getCanchaCounts() : new HashMap<>());
        complejoExistente.setCanchaPrices(complejoDetails.getCanchaPrices() != null ? complejoDetails.getCanchaPrices() : new HashMap<>());
        complejoExistente.setCanchaSurfaces(complejoDetails.getCanchaSurfaces() != null ? complejoDetails.getCanchaSurfaces() : new HashMap<>());
        complejoExistente.setCanchaIluminacion(complejoDetails.getCanchaIluminacion() != null ? complejoDetails.getCanchaIluminacion() : new HashMap<>());
        complejoExistente.setCanchaTecho(complejoDetails.getCanchaTecho() != null ? complejoDetails.getCanchaTecho() : new HashMap<>());

        return complejoRepositorio.save(complejoExistente);
    }

    @Transactional
    public void eliminarComplejo(Long id) {
        log.info("Eliminando complejo con ID: {}", id);
        if (complejoRepositorio.existsById(id)) {
            complejoRepositorio.deleteById(id);
            log.info("Complejo con ID {} eliminado exitosamente.", id);
        } else {
            throw new IllegalArgumentException("Complejo no encontrado con ID: " + id);
        }
    }
}