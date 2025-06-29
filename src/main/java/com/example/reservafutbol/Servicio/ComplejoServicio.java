package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Modelo.Complejo;
import com.example.reservafutbol.Modelo.ERole;
import com.example.reservafutbol.Modelo.Role;
import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Repositorio.ComplejoRepositorio;
import com.example.reservafutbol.Repositorio.RoleRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile; // Importado para manejar archivos

import java.time.LocalTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.io.IOException; // Importado para manejar excepciones de I/O

@Service
public class ComplejoServicio {

    private static final Logger log = LoggerFactory.getLogger(ComplejoServicio.class);

    @Autowired
    private ComplejoRepositorio complejoRepositorio;

    @Autowired
    private UsuarioServicio usuarioServicio;

    @Autowired
    private S3StorageService s3StorageService; // ¡Inyectamos el nuevo servicio S3!

    // No necesitas RoleRepositorio aquí si no lo usas directamente

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

    /**
     * Método para crear un complejo por un ADMIN. Permite la subida de una foto.
     *
     * @param nombreComplejo Nombre del complejo.
     * @param propietarioUsername Email del propietario.
     * @param descripcion Descripción.
     * @param ubicacion Ubicación.
     * @param telefono Teléfono.
     * @param photoFile Archivo de foto (MultipartFile) opcional.
     * @param horarioApertura Horario de apertura.
     * @param horarioCierre Horario de cierre.
     * @param canchaCounts Map de cantidades de canchas.
     * @param canchaPrices Map de precios de canchas.
     * @param canchaSurfaces Map de superficies de canchas.
     * @param canchaIluminacion Map de iluminación de canchas.
     * @param canchaTecho Map de techos de canchas.
     * @return El complejo creado.
     * @throws IllegalArgumentException si los datos son inválidos o el propietario no existe.
     * @throws IOException si hay un error al procesar/subir la imagen.
     */
    @Transactional
    public Complejo crearComplejoParaAdmin(String nombreComplejo, String propietarioUsername,
                                           String descripcion, String ubicacion, String telefono,
                                           MultipartFile photoFile, // ¡CAMBIO AQUÍ! Ahora acepta un MultipartFile
                                           LocalTime horarioApertura, LocalTime horarioCierre,
                                           Map<String, Integer> canchaCounts,
                                           Map<String, Double> canchaPrices,
                                           Map<String, String> canchaSurfaces,
                                           Map<String, Boolean> canchaIluminacion,
                                           Map<String, Boolean> canchaTecho) throws IOException { // ¡CAMBIO AQUÍ! Puede lanzar IOException
        log.info("ADMIN creando complejo '{}' para propietario '{}'", nombreComplejo, propietarioUsername);

        if (nombreComplejo == null || nombreComplejo.isBlank()) {
            throw new IllegalArgumentException("El nombre del complejo es obligatorio.");
        }
        if (complejoRepositorio.findByNombre(nombreComplejo).isPresent()) {
            throw new IllegalArgumentException("Ya existe un complejo con el nombre: " + nombreComplejo);
        }

        User propietario = usuarioServicio.findByUsername(propietarioUsername)
                .orElseThrow(() -> new IllegalArgumentException("Propietario no encontrado con username: " + propietarioUsername));

        Set<ERole> desiredRoles = new HashSet<>();
        desiredRoles.add(ERole.ROLE_USER);
        desiredRoles.add(ERole.ROLE_COMPLEX_OWNER);

        usuarioServicio.updateUserRoles(propietario.getId(), desiredRoles); // Asegura que el propietario tenga los roles correctos

        Complejo nuevoComplejo = new Complejo();
        nuevoComplejo.setNombre(nombreComplejo);
        nuevoComplejo.setPropietario(propietario);

        nuevoComplejo.setDescripcion(descripcion);
        nuevoComplejo.setUbicacion(ubicacion);
        nuevoComplejo.setTelefono(telefono);

        // ¡CAMBIO CLAVE AQUÍ! Subir y procesar la imagen si existe
        if (photoFile != null && !photoFile.isEmpty()) {
            String photoUrl = s3StorageService.uploadComplexImage(photoFile);
            nuevoComplejo.setFotoUrl(photoUrl);
        } else {
            nuevoComplejo.setFotoUrl(null); // O podrías poner una URL por defecto
        }

        nuevoComplejo.setHorarioApertura(horarioApertura != null ? horarioApertura : LocalTime.of(8, 0));
        nuevoComplejo.setHorarioCierre(horarioCierre != null ? horarioCierre : LocalTime.of(22, 0));

        nuevoComplejo.setCanchaCounts(canchaCounts != null ? new HashMap<>(canchaCounts) : new HashMap<>());
        nuevoComplejo.setCanchaPrices(canchaPrices != null ? new HashMap<>(canchaPrices) : new HashMap<>());
        nuevoComplejo.setCanchaSurfaces(canchaSurfaces != null ? new HashMap<>(canchaSurfaces) : new HashMap<>());
        nuevoComplejo.setCanchaIluminacion(canchaIluminacion != null ? new HashMap<>(canchaIluminacion) : new HashMap<>());
        nuevoComplejo.setCanchaTecho(canchaTecho != null ? new HashMap<>(canchaTecho) : new HashMap<>());

        return complejoRepositorio.save(nuevoComplejo);
    }

    @Transactional(readOnly = true)
    public List<Complejo> listarTodosLosComplejos() {
        log.info("Listando todos los complejos (con propietario EAGER).");
        return complejoRepositorio.findAll();
    }

    @Transactional(readOnly = true)
    public List<Complejo> listarComplejosPorPropietario(String propietarioUsername) {
        log.info("Listando complejos para propietario: {} (con propietario EAGER).", propietarioUsername);
        User propietario = usuarioServicio.findByUsername(propietarioUsername)
                .orElseThrow(() -> new IllegalArgumentException("Propietario no encontrado con username: " + propietarioUsername));
        return complejoRepositorio.findByPropietario(propietario);
    }

    @Transactional(readOnly = true)
    public Optional<Complejo> buscarComplejoPorId(Long id) {
        log.info("Buscando complejo por ID: {}.", id);
        return complejoRepositorio.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Complejo> buscarComplejoPorIdWithPropietario(Long id) {
        log.info("Buscando complejo por ID: {} (con propietario cargado explícitamente para controlador/seguridad).", id);
        return complejoRepositorio.findById(id);
    }

    /**
     * Método para actualizar un complejo. Permite la actualización de la foto.
     *
     * @param id ID del complejo a actualizar.
     * @param complejoDetails Detalles del complejo (sin la URL de la foto).
     * @param photoFile Archivo de foto (MultipartFile) opcional para actualizar.
     * @param editorUsername Nombre de usuario de quien edita.
     * @return El complejo actualizado.
     * @throws IllegalArgumentException si el complejo no existe o los datos son inválidos.
     * @throws SecurityException si el usuario no tiene permisos.
     * @throws IOException si hay un error al procesar/subir la imagen.
     */
    @Transactional
    public Complejo actualizarComplejo(Long id, Complejo complejoDetails, MultipartFile photoFile, String editorUsername) throws IOException { // ¡CAMBIO AQUÍ! Acepta MultipartFile y lanza IOException
        log.info("Actualizando complejo con ID: {} por usuario: {}", id, editorUsername);

        Complejo complejoExistente = complejoRepositorio.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Complejo no encontrado con ID: " + id));

        User editor = usuarioServicio.findByUsername(editorUsername)
                .orElseThrow(() -> new IllegalArgumentException("Usuario editor no encontrado: " + editorUsername));

        boolean isAdmin = editor.getRoles().stream().anyMatch(r -> r.getName().equals(ERole.ROLE_ADMIN));
        boolean isOwner = complejoExistente.getPropietario() != null && complejoExistente.getPropietario().getId().equals(editor.getId());

        if (!isAdmin && !isOwner) {
            throw new SecurityException("Acceso denegado: No tienes permisos para actualizar este complejo.");
        }

        complejoExistente.setNombre(complejoDetails.getNombre());
        complejoExistente.setDescripcion(complejoDetails.getDescripcion());
        complejoExistente.setUbicacion(complejoDetails.getUbicacion());
        complejoExistente.setTelefono(complejoDetails.getTelefono());

        // ¡CAMBIO CLAVE AQUÍ! Subir y procesar nueva imagen si se proporciona
        if (photoFile != null && !photoFile.isEmpty()) {
            // Opcional: Eliminar la foto antigua de S3 si existe
            if (complejoExistente.getFotoUrl() != null && !complejoExistente.getFotoUrl().isBlank()) {
                s3StorageService.deleteFile(complejoExistente.getFotoUrl());
            }
            String newPhotoUrl = s3StorageService.uploadComplexImage(photoFile);
            complejoExistente.setFotoUrl(newPhotoUrl);
        } else if (complejoDetails.getFotoUrl() != null && complejoDetails.getFotoUrl().isEmpty()) {
            // Si el frontend envía fotoUrl vacía explícitamente, significa que la quieren quitar
            if (complejoExistente.getFotoUrl() != null && !complejoExistente.getFotoUrl().isBlank()) {
                s3StorageService.deleteFile(complejoExistente.getFotoUrl());
            }
            complejoExistente.setFotoUrl(null);
        }
        // Si photoFile es nulo/vacío Y complejoDetails.getFotoUrl() no es nulo/vacío,
        // significa que no se cambió la foto, y se mantiene la existente.

        complejoExistente.setHorarioApertura(complejoDetails.getHorarioApertura());
        complejoExistente.setHorarioCierre(complejoDetails.getHorarioCierre());

        complejoExistente.setCanchaCounts(complejoDetails.getCanchaCounts() != null ? new HashMap<>(complejoDetails.getCanchaCounts()) : new HashMap<>());
        complejoExistente.setCanchaPrices(complejoDetails.getCanchaPrices() != null ? new HashMap<>(complejoDetails.getCanchaPrices()) : new HashMap<>());
        complejoExistente.setCanchaSurfaces(complejoDetails.getCanchaSurfaces() != null ? new HashMap<>(complejoDetails.getCanchaSurfaces()) : new HashMap<>());
        complejoExistente.setCanchaIluminacion(complejoDetails.getCanchaIluminacion() != null ? new HashMap<>(complejoDetails.getCanchaIluminacion()) : new HashMap<>());
        complejoExistente.setCanchaTecho(complejoDetails.getCanchaTecho() != null ? new HashMap<>(complejoDetails.getCanchaTecho()) : new HashMap<>());

        return complejoRepositorio.save(complejoExistente);
    }

    @Transactional
    public void eliminarComplejo(Long id, String eliminadorUsername) {
        log.info("Eliminando complejo con ID: {} por usuario: {}", id, eliminadorUsername);

        Complejo complejoExistente = complejoRepositorio.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Complejo no encontrado con ID: " + id));

        User eliminador = usuarioServicio.findByUsername(eliminadorUsername)
                .orElseThrow(() -> new IllegalArgumentException("Usuario eliminador no encontrado: " + eliminadorUsername));

        boolean isAdmin = eliminador.getRoles().stream().anyMatch(r -> r.getName().equals(ERole.ROLE_ADMIN));
        boolean isOwner = complejoExistente.getPropietario() != null && complejoExistente.getPropietario().getId().equals(eliminador.getId());

        if (!isAdmin && !isOwner) {
            throw new SecurityException("Acceso denegado: No tienes permisos para eliminar este complejo.");
        }

        // ¡Opcional: Eliminar la foto de S3 al eliminar el complejo!
        if (complejoExistente.getFotoUrl() != null && !complejoExistente.getFotoUrl().isBlank()) {
            try {
                s3StorageService.deleteFile(complejoExistente.getFotoUrl());
            } catch (Exception e) {
                log.error("Error al intentar eliminar la foto de S3 para el complejo ID {}: {}", id, e.getMessage());
                // No lanzar la excepción para no impedir la eliminación del complejo en la BD
            }
        }

        complejoRepositorio.deleteById(id);
        log.info("Complejo con ID {} eliminado exitosamente.", id);
    }
}