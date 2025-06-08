package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Modelo.Complejo;
import com.example.reservafutbol.Modelo.ERole; // Importar ERole
import com.example.reservafutbol.Modelo.User; // Importar User
import com.example.reservafutbol.Repositorio.ComplejoRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@Service
public class ComplejoServicio {

    private static final Logger log = LoggerFactory.getLogger(ComplejoServicio.class);

    @Autowired
    private ComplejoRepositorio complejoRepositorio;

    // Inyectar UsuarioServicio para buscar el propietario
    @Autowired
    private UsuarioServicio usuarioServicio;

    @Transactional
    public Complejo crearComplejo(Complejo complejo, String propietarioUsername) {
        log.info("Creando nuevo complejo: {} para propietario: {}", complejo.getNombre(), propietarioUsername);

        if (complejo.getNombre() == null || complejo.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del complejo es obligatorio.");
        }
        if (complejo.getHorarioApertura() == null || complejo.getHorarioCierre() == null) {
            throw new IllegalArgumentException("El horario de apertura y cierre del complejo es obligatorio.");
        }

        // Buscar y asignar el propietario (User)
        User propietario = usuarioServicio.findByUsername(propietarioUsername)
                .orElseThrow(() -> new IllegalArgumentException("Propietario no encontrado con username: " + propietarioUsername));
        complejo.setPropietario(propietario); // Asignar el propietario al complejo

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

    // Nuevo método: Listar complejos por propietario
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
    // Agregamos el username del usuario que intenta actualizar para verificar propiedad
    public Complejo actualizarComplejo(Long id, Complejo complejoDetails, String editorUsername) {
        log.info("Actualizando complejo con ID: {} por usuario: {}", id, editorUsername);

        Complejo complejoExistente = complejoRepositorio.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Complejo no encontrado con ID: " + id));

        // Obtener el usuario que está intentando actualizar
        User editor = usuarioServicio.findByUsername(editorUsername)
                .orElseThrow(() -> new IllegalArgumentException("Usuario editor no encontrado: " + editorUsername));

        // --- Lógica de Autorización a Nivel de Recurso ---
        // Un ADMIN puede actualizar cualquier complejo. Un COMPLEX_OWNER solo puede actualizar los suyos.
        if (editor.getRoles().stream().noneMatch(r -> r.getName().equals(ERole.ROLE_ADMIN))) {
            // Si el editor NO es ADMIN, debe ser el propietario del complejo
            if (complejoExistente.getPropietario() == null || !complejoExistente.getPropietario().getId().equals(editor.getId())) {
                throw new SecurityException("Acceso denegado: No tienes permisos para actualizar este complejo.");
            }
        }

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
    // Agregamos el username del usuario que intenta eliminar para verificar propiedad
    public void eliminarComplejo(Long id, String eliminadorUsername) {
        log.info("Eliminando complejo con ID: {} por usuario: {}", id, eliminadorUsername);

        Complejo complejoExistente = complejoRepositorio.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Complejo no encontrado con ID: " + id));

        // Obtener el usuario que está intentando eliminar
        User eliminador = usuarioServicio.findByUsername(eliminadorUsername)
                .orElseThrow(() -> new IllegalArgumentException("Usuario eliminador no encontrado: " + eliminadorUsername));

        // --- Lógica de Autorización a Nivel de Recurso ---
        if (eliminador.getRoles().stream().noneMatch(r -> r.getName().equals(ERole.ROLE_ADMIN))) {
            // Si el eliminador NO es ADMIN, debe ser el propietario del complejo
            if (complejoExistente.getPropietario() == null || !complejoExistente.getPropietario().getId().equals(eliminador.getId())) {
                throw new SecurityException("Acceso denegado: No tienes permisos para eliminar este complejo.");
            }
        }

        complejoRepositorio.deleteById(id);
        log.info("Complejo con ID {} eliminado exitosamente.", id);
    }
}