package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Modelo.Complejo;
import com.example.reservafutbol.Modelo.ERole;
import com.example.reservafutbol.Modelo.Role;
import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Repositorio.ComplejoRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalTime;
import java.util.*;
import java.io.IOException;
import java.util.stream.Collectors;

@Service
public class ComplejoServicio {

    private static final Logger log = LoggerFactory.getLogger(ComplejoServicio.class);

    @Autowired
    private ComplejoRepositorio complejoRepositorio;

    @Autowired
    private UsuarioServicio usuarioServicio;

    @Autowired
    private S3StorageService s3StorageService;

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
     * Método para crear un complejo por un ADMIN. Permite la subida de una foto de portada y múltiples fotos de carrusel.
     *
     * @param nombreComplejo Nombre del complejo.
     * @param propietarioUsername Email del propietario.
     * @param descripcion Descripción.
     * @param ubicacion Ubicación.
     * @param telefono Teléfono.
     * @param coverPhoto Archivo de imagen para la portada (MultipartFile) opcional.
     * @param carouselPhotos Array de archivos de imagen (MultipartFile[]) opcional para el carrusel.
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
                                           MultipartFile coverPhoto, MultipartFile[] carouselPhotos,
                                           LocalTime horarioApertura, LocalTime horarioCierre,
                                           Map<String, Integer> canchaCounts,
                                           Map<String, Double> canchaPrices,
                                           Map<String, String> canchaSurfaces,
                                           Map<String, Boolean> canchaIluminacion,
                                           Map<String, Boolean> canchaTecho) throws IOException {
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

        usuarioServicio.updateUserRoles(propietario.getId(), desiredRoles);

        Complejo nuevoComplejo = new Complejo();
        nuevoComplejo.setNombre(nombreComplejo);
        nuevoComplejo.setPropietario(propietario);

        nuevoComplejo.setDescripcion(descripcion);
        nuevoComplejo.setUbicacion(ubicacion);
        nuevoComplejo.setTelefono(telefono);

        Map<String, String> fotoUrlsPorResolucion = new HashMap<>();

        // Subir imagen de portada si existe
        if (coverPhoto != null && !coverPhoto.isEmpty()) {
            Map<String, String> coverPhotoUrls = s3StorageService.uploadSingleImageWithResolutions(coverPhoto, "cover/");
            fotoUrlsPorResolucion.putAll(coverPhotoUrls); // Añade original y thumbnail de la portada
        }

        // Subir imágenes del carrusel si existen
        if (carouselPhotos != null && carouselPhotos.length > 0) {
            List<Map<String, String>> uploadedCarouselImages = s3StorageService.uploadCarouselImagesWithResolutions(carouselPhotos);
            for (Map<String, String> imgMap : uploadedCarouselImages) {
                // Generar una clave única para cada imagen del carrusel, por ejemplo, "carousel_uuid"
                fotoUrlsPorResolucion.put("carousel_" + UUID.randomUUID().toString(), imgMap.get("original"));
                // También podrías guardar thumbnails si los necesitas para el carrusel
            }
        }
        nuevoComplejo.setFotoUrlsPorResolucion(fotoUrlsPorResolucion);

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
        log.info("Listando todos los complejos.");
        return complejoRepositorio.findAll();
    }

    @Transactional(readOnly = true)
    public List<Complejo> listarComplejosPorPropietario(String propietarioUsername) {
        log.info("Listando complejos para propietario: {}.", propietarioUsername);
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
     * Método para actualizar un complejo. Permite la actualización de la foto de portada y las fotos del carrusel.
     *
     * @param id ID del complejo a actualizar.
     * @param complejoDetails Detalles del complejo (sin las URLs de las fotos, estas se manejan por separado).
     * @param coverPhoto Archivo de imagen para la portada (MultipartFile) opcional para actualizar.
     * @param carouselPhotos Array de archivos de imagen (MultipartFile[]) opcional para actualizar el carrusel.
     * @param editorUsername Nombre de usuario de quien edita.
     * @return El complejo actualizado.
     * @throws IllegalArgumentException si el complejo no existe o los datos son inválidos.
     * @throws SecurityException si el usuario no tiene permisos.
     * @throws IOException si hay un error al procesar/subir la imagen.
     */
    @Transactional
    public Complejo actualizarComplejo(Long id, Complejo complejoDetails,
                                       MultipartFile coverPhoto, MultipartFile[] carouselPhotos,
                                       String editorUsername) throws IOException {
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

        Map<String, String> fotoUrlsActualizadas = new HashMap<>();
        if (complejoExistente.getFotoUrlsPorResolucion() != null) {
            fotoUrlsActualizadas.putAll(complejoExistente.getFotoUrlsPorResolucion());
        }

        // --- Manejo de la imagen de portada ---
        if (coverPhoto != null && !coverPhoto.isEmpty()) {
            // Eliminar la portada antigua si existe
            if (fotoUrlsActualizadas.containsKey("original")) {
                s3StorageService.deleteFile(fotoUrlsActualizadas.get("original"));
                fotoUrlsActualizadas.remove("original");
                fotoUrlsActualizadas.remove("thumbnail"); // También eliminar su thumbnail
            }
            Map<String, String> newCoverUrls = s3StorageService.uploadSingleImageWithResolutions(coverPhoto, "cover/");
            fotoUrlsActualizadas.putAll(newCoverUrls);
        } else if (complejoDetails.getFotoUrlsPorResolucion() != null && !complejoDetails.getFotoUrlsPorResolucion().containsKey("original")) {
            // Si el frontend indica que la portada fue eliminada explícitamente
            if (fotoUrlsActualizadas.containsKey("original")) {
                s3StorageService.deleteFile(fotoUrlsActualizadas.get("original"));
                fotoUrlsActualizadas.remove("original");
                fotoUrlsActualizadas.remove("thumbnail");
            }
        }
        // Si coverPhoto es nulo/vacío y complejoDetails.getFotoUrlsPorResolucion() contiene 'original',
        // significa que no se cambió y se mantiene la existente.

        // --- Manejo de las imágenes del carrusel ---
        if (carouselPhotos != null && carouselPhotos.length > 0) {
            // Eliminar todas las fotos de carrusel antiguas (las que no son "original" ni "thumbnail")
            List<String> keysToRemove = fotoUrlsActualizadas.keySet().stream()
                    .filter(key -> key.startsWith("carousel_"))
                    .collect(Collectors.toList());
            for (String key : keysToRemove) {
                s3StorageService.deleteFile(fotoUrlsActualizadas.get(key));
                fotoUrlsActualizadas.remove(key);
            }

            List<Map<String, String>> uploadedCarouselImages = s3StorageService.uploadCarouselImagesWithResolutions(carouselPhotos);
            for (Map<String, String> imgMap : uploadedCarouselImages) {
                fotoUrlsActualizadas.put("carousel_" + UUID.randomUUID().toString(), imgMap.get("original"));
            }
        } else if (complejoDetails.getFotoUrlsPorResolucion() != null && complejoDetails.getFotoUrlsPorResolucion().keySet().stream().noneMatch(key -> key.startsWith("carousel_"))) {
            // Si el frontend indica que las fotos del carrusel fueron eliminadas explícitamente
            List<String> keysToRemove = fotoUrlsActualizadas.keySet().stream()
                    .filter(key -> key.startsWith("carousel_"))
                    .collect(Collectors.toList());
            for (String key : keysToRemove) {
                s3StorageService.deleteFile(fotoUrlsActualizadas.get(key));
                fotoUrlsActualizadas.remove(key);
            }
        }
        // Si carouselPhotos es nulo/vacío y complejoDetails.getFotoUrlsPorResolucion() contiene 'carousel_',
        // significa que no se cambió y se mantienen las existentes.

        complejoExistente.setFotoUrlsPorResolucion(fotoUrlsActualizadas);

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

        if (complejoExistente.getFotoUrlsPorResolucion() != null && !complejoExistente.getFotoUrlsPorResolucion().isEmpty()) {
            try {
                for (String url : complejoExistente.getFotoUrlsPorResolucion().values()) {
                    s3StorageService.deleteFile(url);
                }
            } catch (Exception e) {
                log.error("Error al intentar eliminar las fotos de S3 para el complejo ID {}: {}", id, e.getMessage());
            }
        }

        complejoRepositorio.deleteById(id);
        log.info("Complejo con ID {} eliminado exitosamente.", id);
    }
}
