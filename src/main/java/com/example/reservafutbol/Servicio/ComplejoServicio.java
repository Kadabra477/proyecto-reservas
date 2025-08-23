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

        if (coverPhoto != null && !coverPhoto.isEmpty()) {
            Map<String, String> coverPhotoUrls = s3StorageService.uploadImageWithResolutions(coverPhoto, "cover/");
            fotoUrlsPorResolucion.putAll(coverPhotoUrls);
        }

        if (carouselPhotos != null && carouselPhotos.length > 0) {
            List<Map<String, String>> uploadedCarouselImages = s3StorageService.uploadMultipleImagesWithResolutions(carouselPhotos, "carousel/");
            uploadedCarouselImages.forEach(imgMap -> {
                fotoUrlsPorResolucion.put("carousel_" + UUID.randomUUID().toString(), imgMap.get("original"));
            });
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

        Map<String, String> fotoUrlsActualizadas = complejoExistente.getFotoUrlsPorResolucion() != null ? new HashMap<>(complejoExistente.getFotoUrlsPorResolucion()) : new HashMap<>();

        if (coverPhoto != null && !coverPhoto.isEmpty()) {
            if (fotoUrlsActualizadas.containsKey("original")) {
                s3StorageService.deleteFile(fotoUrlsActualizadas.get("original"));
                fotoUrlsActualizadas.remove("original");
            }
            if (fotoUrlsActualizadas.containsKey("thumbnail")) {
                s3StorageService.deleteFile(fotoUrlsActualizadas.get("thumbnail"));
                fotoUrlsActualizadas.remove("thumbnail");
            }
            Map<String, String> newCoverUrls = s3StorageService.uploadImageWithResolutions(coverPhoto, "cover/");
            fotoUrlsActualizadas.putAll(newCoverUrls);
        } else if (complejoDetails.getFotoUrlsPorResolucion() != null && !complejoDetails.getFotoUrlsPorResolucion().containsKey("original")) {
            if (fotoUrlsActualizadas.containsKey("original")) {
                s3StorageService.deleteFile(fotoUrlsActualizadas.get("original"));
                fotoUrlsActualizadas.remove("original");
                fotoUrlsActualizadas.remove("thumbnail");
            }
        }

        if (carouselPhotos != null && carouselPhotos.length > 0) {
            List<String> keysToRemove = fotoUrlsActualizadas.keySet().stream()
                    .filter(key -> key.startsWith("carousel_"))
                    .collect(Collectors.toList());
            for (String key : keysToRemove) {
                s3StorageService.deleteFile(fotoUrlsActualizadas.get(key));
                fotoUrlsActualizadas.remove(key);
            }

            List<Map<String, String>> uploadedCarouselImages = s3StorageService.uploadMultipleImagesWithResolutions(carouselPhotos, "carousel/");
            uploadedCarouselImages.forEach(imgMap -> {
                fotoUrlsActualizadas.put("carousel_" + UUID.randomUUID().toString(), imgMap.get("original"));
            });
        } else if (complejoDetails.getFotoUrlsPorResolucion() != null && complejoDetails.getFotoUrlsPorResolucion().keySet().stream().noneMatch(key -> key.startsWith("carousel_"))) {
            List<String> keysToRemove = fotoUrlsActualizadas.keySet().stream()
                    .filter(key -> key.startsWith("carousel_"))
                    .collect(Collectors.toList());
            for (String key : keysToRemove) {
                s3StorageService.deleteFile(fotoUrlsActualizadas.get(key));
                fotoUrlsActualizadas.remove(key);
            }
        }

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