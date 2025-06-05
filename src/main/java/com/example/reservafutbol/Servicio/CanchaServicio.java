package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Modelo.Cancha;
import com.example.reservafutbol.Repositorio.CanchaRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory; // Asegúrate de importar LoggerFactory
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CanchaServicio {

    private static final Logger log = LoggerFactory.getLogger(CanchaServicio.class); // Declaración del logger

    @Autowired
    private CanchaRepositorio canchaRepositorio;

    // Obtener todas las canchas (útil para el listado general y para obtener tipos únicos)
    public List<Cancha> listarTodasCanchas() {
        log.info("Listando todas las canchas.");
        return canchaRepositorio.findAll();
    }

    public Optional<Cancha> buscarPorId(Long id) {
        log.info("Buscando cancha por ID: {}", id);
        return canchaRepositorio.findById(id);
    }

    // NUEVO MÉTODO: Listar canchas por tipo y que estén generalmente disponibles
    @Transactional(readOnly = true) // Es una operación de solo lectura
    public List<Cancha> listarCanchasPorTipoYDisponibilidad(String tipoCancha) {
        log.info("Listando canchas de tipo '{}' y disponibles.", tipoCancha);
        return canchaRepositorio.findByTipoCanchaAndDisponibleTrue(tipoCancha);
    }

    @Transactional
    public Cancha crearCancha(Cancha cancha) {
        log.info("Creando cancha: {}", cancha.getNombre());
        if (cancha.getNombre() == null || cancha.getNombre().isEmpty()) {
            throw new IllegalArgumentException("El nombre de la cancha no puede estar vacío");
        }
        if (cancha.getTipoCancha() == null || cancha.getTipoCancha().isEmpty()) {
            throw new IllegalArgumentException("El tipo de cancha es obligatorio.");
        }
        if (cancha.getSuperficie() == null || cancha.getSuperficie().isEmpty()) {
            throw new IllegalArgumentException("La superficie de la cancha es obligatoria.");
        }
        if (cancha.getIluminacion() == null) { // Si viene null, establecer a false
            cancha.setIluminacion(false);
        }
        if (cancha.getTecho() == null) { // Si viene null, establecer a false
            cancha.setTecho(false);
        }
        if (cancha.getDisponible() == null) { // Si viene null, asumir disponible por defecto al crear
            cancha.setDisponible(true);
        }

        return canchaRepositorio.save(cancha);
    }

    @Transactional
    public Cancha actualizarCancha(Long id, Cancha canchaDetails) {
        log.info("Actualizando cancha con ID: {}", id);
        Cancha canchaExistente = canchaRepositorio.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cancha no encontrada con ID: " + id));

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
        canchaExistente.setTipoCancha(canchaDetails.getTipoCancha());
        canchaExistente.setSuperficie(canchaDetails.getSuperficie());
        canchaExistente.setIluminacion(canchaDetails.getIluminacion() != null ? canchaDetails.getIluminacion() : false);
        canchaExistente.setTecho(canchaDetails.getTecho() != null ? canchaDetails.getTecho() : false);

        return canchaRepositorio.save(canchaExistente);
    }

    @Transactional
    public void eliminarCancha(Long id) { // Cambiado a void para consistencia con tu uso previo
        log.info("Eliminando cancha con ID: {}", id);
        if (canchaRepositorio.existsById(id)) {
            canchaRepositorio.deleteById(id);
            log.info("Cancha con ID {} eliminada exitosamente.", id);
        } else {
            throw new IllegalArgumentException("Cancha no encontrada con ID: " + id);
        }
    }
}