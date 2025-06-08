package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Modelo.Complejo;
import com.example.reservafutbol.Modelo.ERole;
import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Repositorio.ComplejoRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ComplejoServicio {

    private static final Logger log = LoggerFactory.getLogger(ComplejoServicio.class);

    @Autowired
    private ComplejoRepositorio complejoRepositorio;

    @Autowired
    private UsuarioServicio usuarioServicio;

    @Transactional
    public Complejo crearComplejo(Complejo complejo, String propietarioUsername) {
        log.info("Creando nuevo complejo (detallado): {} para propietario: {}", complejo.getNombre(), propietarioUsername);

        if (complejo.getNombre() == null || complejo.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del complejo es obligatorio.");
        }
        if (complejo.getHorarioApertura() == null) complejo.setHorarioApertura(LocalTime.of(8, 0));
        if (complejo.getHorarioCierre() == null) complejo.setHorarioCierre(LocalTime.of(22, 0));

        User propietario = usuarioServicio.findByUsername(propietarioUsername)
                .orElseThrow(() -> new IllegalArgumentException("Propietario no encontrado con username: " + propietarioUsername));
        complejo.setPropietario(propietario);

        if (complejo.getCanchaCounts() == null) complejo.setCanchaCounts(new HashMap<>());
        if (complejo.getCanchaPrices() == null) complejo.setCanchaPrices(new HashMap<>());
        if (complejo.getCanchaSurfaces() == null) complejo.setCanchaSurfaces(new HashMap<>());
        if (complejo.getCanchaIluminacion() == null) complejo.setCanchaIluminacion(new HashMap<>());
        if (complejo.getCanchaTecho() == null) complejo.setCanchaTecho(new HashMap<>());

        return complejoRepositorio.save(complejo);
    }

    @Transactional
    public Complejo crearComplejoParaAdmin(String nombreComplejo, String propietarioUsername, Map<String, Integer> canchaCounts) {
        log.info("ADMIN creando complejo '{}' para propietario '{}' con canchas: {}", nombreComplejo, propietarioUsername, canchaCounts);

        if (nombreComplejo == null || nombreComplejo.isBlank()) {
            throw new IllegalArgumentException("El nombre del complejo es obligatorio.");
        }
        User propietario = usuarioServicio.findByUsername(propietarioUsername)
                .orElseThrow(() -> new IllegalArgumentException("Propietario no encontrado con username: " + propietarioUsername));

        Complejo nuevoComplejo = new Complejo();
        nuevoComplejo.setNombre(nombreComplejo);
        nuevoComplejo.setPropietario(propietario);

        nuevoComplejo.setHorarioApertura(LocalTime.of(8, 0));
        nuevoComplejo.setHorarioCierre(LocalTime.of(22, 0));
        nuevoComplejo.setCanchaCounts(canchaCounts != null ? canchaCounts : new HashMap<>());
        nuevoComplejo.setCanchaPrices(new HashMap<>());
        nuevoComplejo.setCanchaSurfaces(new HashMap<>());
        nuevoComplejo.setCanchaIluminacion(new HashMap<>());
        nuevoComplejo.setCanchaTecho(new HashMap<>());

        for (Map.Entry<String, Integer> entry : nuevoComplejo.getCanchaCounts().entrySet()) {
            String tipoCancha = entry.getKey();
            if (!nuevoComplejo.getCanchaPrices().containsKey(tipoCancha)) {
                nuevoComplejo.getCanchaPrices().put(tipoCancha, 0.0);
            }
            if (!nuevoComplejo.getCanchaSurfaces().containsKey(tipoCancha)) {
                nuevoComplejo.getCanchaSurfaces().put(tipoCancha, "A definir");
            }
            if (!nuevoComplejo.getCanchaIluminacion().containsKey(tipoCancha)) {
                nuevoComplejo.getCanchaIluminacion().put(tipoCancha, false);
            }
            if (!nuevoComplejo.getCanchaTecho().containsKey(tipoCancha)) {
                nuevoComplejo.getCanchaTecho().put(tipoCancha, false);
            }
        }

        return complejoRepositorio.save(nuevoComplejo);
    }

    @Transactional(readOnly = true)
    public List<Complejo> listarTodosLosComplejos() {
        log.info("Listando todos los complejos.");
        return complejoRepositorio.findAll();
    }

    @Transactional(readOnly = true)
    public List<Complejo> listarComplejosPorPropietario(String propietarioUsername) {
        log.info("Listando complejos para propietario: {}", propietarioUsername);
        User propietario = usuarioServicio.findByUsername(propietarioUsername)
                .orElseThrow(() -> new IllegalArgumentException("Propietario no encontrado con username: " + propietarioUsername));
        return complejoRepositorio.findByPropietario(propietario);
    }

    @Transactional(readOnly = true)
    public Optional<Complejo> buscarComplejoPorId(Long id) {
        log.info("Buscando complejo por ID: {}", id);
        return complejoRepositorio.findById(id);
    }

    @Transactional
    public Complejo actualizarComplejo(Long id, Complejo complejoDetails, String editorUsername) {
        log.info("Actualizando complejo con ID: {} por usuario: {}", id, editorUsername);

        Complejo complejoExistente = complejoRepositorio.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Complejo no encontrado con ID: " + id));

        User editor = usuarioServicio.findByUsername(editorUsername)
                .orElseThrow(() -> new IllegalArgumentException("Usuario editor no encontrado: " + editorUsername));

        if (editor.getRoles().stream().noneMatch(r -> r.getName().equals(ERole.ROLE_ADMIN))) {
            if (complejoExistente.getPropietario() == null || !complejoExistente.getPropietario().getId().equals(editor.getId())) {
                throw new SecurityException("Acceso denegado: No tienes permisos para actualizar este complejo.");
            }
        }

        complejoExistente.setNombre(complejoDetails.getNombre());
        complejoExistente.setDescripcion(complejoDetails.getDescripcion());
        complejoExistente.setUbicacion(complejoDetails.getUbicacion());
        complejoExistente.setTelefono(complejoDetails.getTelefono());
        complejoExistente.setFotoUrl(complejoDetails.getFotoUrl());
        complejoExistente.setHorarioApertura(complejoDetails.getHorarioApertura());
        complejoExistente.setHorarioCierre(complejoDetails.getHorarioCierre());

        complejoExistente.setCanchaCounts(complejoDetails.getCanchaCounts() != null ? complejoDetails.getCanchaCounts() : new HashMap<>());
        complejoExistente.setCanchaPrices(complejoDetails.getCanchaPrices() != null ? complejoDetails.getCanchaPrices() : new HashMap<>());
        complejoExistente.setCanchaSurfaces(complejoDetails.getCanchaSurfaces() != null ? complejoDetails.getCanchaSurfaces() : new HashMap<>());
        complejoExistente.setCanchaIluminacion(complejoDetails.getCanchaIluminacion() != null ? complejoDetails.getCanchaIluminacion() : new HashMap<>());
        complejoExistente.setCanchaTecho(complejoDetails.getCanchaTecho() != null ? complejoDetails.getCanchaTecho() : new HashMap<>());

        return complejoRepositorio.save(complejoExistente);
    }

    @Transactional
    public void eliminarComplejo(Long id, String eliminadorUsername) {
        log.info("Eliminando complejo con ID: {} por usuario: {}", id, eliminadorUsername);

        Complejo complejoExistente = complejoRepositorio.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Complejo no encontrado con ID: " + id));

        User eliminador = usuarioServicio.findByUsername(eliminadorUsername)
                .orElseThrow(() -> new IllegalArgumentException("Usuario eliminador no encontrado: " + eliminadorUsername));

        if (eliminador.getRoles().stream().noneMatch(r -> r.getName().equals(ERole.ROLE_ADMIN))) {
            if (complejoExistente.getPropietario() == null || !complejoExistente.getPropietario().getId().equals(eliminador.getId())) {
                throw new SecurityException("Acceso denegado: No tienes permisos para eliminar este complejo.");
            }
        }

        complejoRepositorio.deleteById(id);
        log.info("Complejo con ID {} eliminado exitosamente.", id);
    }
}