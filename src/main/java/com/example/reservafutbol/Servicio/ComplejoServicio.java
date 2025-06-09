package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Modelo.Complejo;
import com.example.reservafutbol.Modelo.ERole; // Importa ERole
import com.example.reservafutbol.Modelo.Role;
import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Repositorio.ComplejoRepositorio;
import com.example.reservafutbol.Repositorio.RoleRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ComplejoServicio {

    private static final Logger log = LoggerFactory.getLogger(ComplejoServicio.class);

    @Autowired
    private ComplejoRepositorio complejoRepositorio;

    @Autowired
    private UsuarioServicio usuarioServicio;

    @Autowired
    private RoleRepositorio roleRepositorio;

    // Este método ya existe y lo mantendremos por compatibilidad, pero el frontend usará 'crearComplejoParaAdmin'
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

    // Método mejorado para la creación de complejos por el ADMIN, recibiendo todos los detalles
    @Transactional
    public Complejo crearComplejoParaAdmin(String nombreComplejo, String propietarioUsername,
                                           String descripcion, String ubicacion, String telefono, String fotoUrl,
                                           LocalTime horarioApertura, LocalTime horarioCierre,
                                           Map<String, Integer> canchaCounts,
                                           Map<String, Double> canchaPrices,
                                           Map<String, String> canchaSurfaces,
                                           Map<String, Boolean> canchaIluminacion,
                                           Map<String, Boolean> canchaTecho) {
        log.info("ADMIN creando complejo '{}' para propietario '{}'", nombreComplejo, propietarioUsername);

        if (nombreComplejo == null || nombreComplejo.isBlank()) {
            throw new IllegalArgumentException("El nombre del complejo es obligatorio.");
        }
        if (complejoRepositorio.findByNombre(nombreComplejo).isPresent()) {
            throw new IllegalArgumentException("Ya existe un complejo con el nombre: " + nombreComplejo);
        }

        User propietario = usuarioServicio.findByUsername(propietarioUsername)
                .orElseThrow(() -> new IllegalArgumentException("Propietario no encontrado con username: " + propietarioUsername));

        // Asignar el rol OWNER si el propietario no lo tiene
        // **CORRECCIÓN:** Se usa ERole.ROLE_OWNER para referenciar el enum.
        Role ownerRole = roleRepositorio.findByName(ERole.ROLE_ADMIN)
                .orElseThrow(() -> new RuntimeException("Error: Rol OWNER no encontrado."));

        Set<Role> roles = new HashSet<>(propietario.getRoles());
        if (!roles.contains(ownerRole)) {
            roles.add(ownerRole);
            propietario.setRoles(roles);
            // **CORRECCIÓN:** Se usa .save() en lugar de .guardarUsuario()
            usuarioServicio.save(propietario);
            log.info("Asignado rol COMPLEX_OWNER a usuario: {}", propietarioUsername);
        }

        Complejo nuevoComplejo = new Complejo();
        nuevoComplejo.setNombre(nombreComplejo);
        nuevoComplejo.setPropietario(propietario);

        // Asignar detalles generales
        nuevoComplejo.setDescripcion(descripcion);
        nuevoComplejo.setUbicacion(ubicacion);
        nuevoComplejo.setTelefono(telefono);
        nuevoComplejo.setFotoUrl(fotoUrl);
        nuevoComplejo.setHorarioApertura(horarioApertura != null ? horarioApertura : LocalTime.of(8, 0));
        nuevoComplejo.setHorarioCierre(horarioCierre != null ? horarioCierre : LocalTime.of(22, 0));

        // Asignar detalles de canchas
        nuevoComplejo.setCanchaCounts(canchaCounts != null ? canchaCounts : new HashMap<>());
        nuevoComplejo.setCanchaPrices(canchaPrices != null ? canchaPrices : new HashMap<>());
        nuevoComplejo.setCanchaSurfaces(canchaSurfaces != null ? canchaSurfaces : new HashMap<>());
        nuevoComplejo.setCanchaIluminacion(canchaIluminacion != null ? canchaIluminacion : new HashMap<>());
        nuevoComplejo.setCanchaTecho(canchaTecho != null ? canchaTecho : new HashMap<>());

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

        // Verificar permisos: ADMIN o el PROPIETARIO del complejo
        boolean isAdmin = editor.getRoles().stream().anyMatch(r -> r.getName().equals(ERole.ROLE_ADMIN));
        boolean isOwner = complejoExistente.getPropietario() != null && complejoExistente.getPropietario().getId().equals(editor.getId());

        if (!isAdmin && !isOwner) {
            throw new SecurityException("Acceso denegado: No tienes permisos para actualizar este complejo.");
        }

        complejoExistente.setNombre(complejoDetails.getNombre());
        complejoExistente.setDescripcion(complejoDetails.getDescripcion());
        complejoExistente.setUbicacion(complejoDetails.getUbicacion());
        complejoExistente.setTelefono(complejoDetails.getTelefono());
        complejoExistente.setFotoUrl(complejoDetails.getFotoUrl());
        complejoExistente.setHorarioApertura(complejoDetails.getHorarioApertura());
        complejoExistente.setHorarioCierre(complejoDetails.getHorarioCierre());

        // Actualizar los mapas de canchas. Asegurarse de que si se envían nulos, se inicialicen.
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

        // Verificar permisos: ADMIN o el PROPIETARIO del complejo
        boolean isAdmin = eliminador.getRoles().stream().anyMatch(r -> r.getName().equals(ERole.ROLE_ADMIN));
        boolean isOwner = complejoExistente.getPropietario() != null && complejoExistente.getPropietario().getId().equals(eliminador.getId());

        if (!isAdmin && !isOwner) {
            throw new SecurityException("Acceso denegado: No tienes permisos para eliminar este complejo.");
        }

        complejoRepositorio.deleteById(id);
        log.info("Complejo con ID {} eliminado exitosamente.", id);
    }
}