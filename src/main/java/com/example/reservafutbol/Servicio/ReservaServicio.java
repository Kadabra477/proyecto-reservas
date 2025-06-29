package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Modelo.Complejo;
import com.example.reservafutbol.Modelo.ERole;
import com.example.reservafutbol.Modelo.Reserva;
import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Repositorio.ComplejoRepositorio;
import com.example.reservafutbol.Repositorio.ReservaRepositorio;
import com.example.reservafutbol.Repositorio.UsuarioRepositorio;
import com.example.reservafutbol.payload.response.EstadisticasResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ReservaServicio {

    private static final Logger log = LoggerFactory.getLogger(ReservaServicio.class);

    @Autowired
    private ReservaRepositorio reservaRepositorio;

    @Autowired
    private UsuarioRepositorio usuarioRepositorio;

    @Autowired
    private ComplejoRepositorio complejoRepositorio;

    @Autowired
    private EmailService emailService;

    @Value("${admin.email}")
    private String adminEmail;

    private static final int SLOT_DURATION_MINUTES = 60;

    @Transactional(readOnly = true)
    public List<Reserva> listarReservasPorComplejo(Long complejoId, String requesterUsername) {
        log.info("Buscando reservas para complejo ID: {} por usuario: {}", complejoId, requesterUsername);
        User requester = usuarioRepositorio.findByUsername(requesterUsername)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + requesterUsername));

        if (requester.getRoles().stream().anyMatch(r -> r.getName().equals(ERole.ROLE_ADMIN))) {
            return reservaRepositorio.findByComplejoId(complejoId);
        }

        if (requester.getRoles().stream().anyMatch(r -> r.getName().equals(ERole.ROLE_COMPLEX_OWNER))) {
            Complejo complejo = complejoRepositorio.findById(complejoId)
                    .orElseThrow(() -> new IllegalArgumentException("Complejo no encontrado con ID: " + complejoId));
            if (complejo.getPropietario() == null || !complejo.getPropietario().getId().equals(requester.getId())) {
                throw new SecurityException("Acceso denegado: Este complejo no te pertenece.");
            }
            return reservaRepositorio.findByComplejoId(complejoId);
        }

        return Collections.emptyList();
    }

    @Transactional(readOnly = true)
    public List<Reserva> listarReservasPorComplejoYTipo(Long complejoId, String tipoCancha, String requesterUsername) {
        log.info("Buscando reservas de tipo '{}' en complejo ID: {} por usuario: {}", tipoCancha, complejoId, requesterUsername);
        User requester = usuarioRepositorio.findByUsername(requesterUsername)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + requesterUsername));

        if (requester.getRoles().stream().noneMatch(r -> r.getName().equals(ERole.ROLE_ADMIN))) {
            Complejo complejo = complejoRepositorio.findById(complejoId)
                    .orElseThrow(() -> new IllegalArgumentException("Complejo no encontrado con ID: " + complejoId));
            if (complejo.getPropietario() == null || !complejo.getPropietario().getId().equals(requester.getId())) {
                throw new SecurityException("Acceso denegado: Este complejo no te pertenece.");
            }
        }

        return reservaRepositorio.findAll().stream()
                .filter(r -> r.getComplejo().getId().equals(complejoId) && r.getTipoCanchaReservada().equals(tipoCancha))
                .collect(Collectors.toList());
    }

    @Transactional
    public Reserva crearReserva(Reserva reserva) {
        if (reserva.getUsuario() == null && (reserva.getUserEmail() == null || reserva.getUserEmail().isBlank())) {
            throw new IllegalArgumentException("La reserva debe estar asociada a un usuario (email).");
        }
        if (reserva.getFechaHora() == null) {
            throw new IllegalArgumentException("La fecha y hora son obligatorias.");
        }
        if (reserva.getPrecio() == null || reserva.getPrecio().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El precio de la reserva debe ser válido.");
        }
        if (reserva.getMetodoPago() == null || reserva.getMetodoPago().isBlank()) {
            throw new IllegalArgumentException("El método de pago es obligatorio.");
        }

        if (reserva.getComplejo() == null || reserva.getComplejo().getId() == null) {
            log.error("Error: Reserva recibida sin un complejo asignado. El controlador debe asignar el complejo antes de llamar a crearReserva.");
            throw new IllegalStateException("Error interno al procesar la reserva: complejo no asignado.");
        }
        if (reserva.getTipoCanchaReservada() == null || reserva.getTipoCanchaReservada().isBlank()) {
            log.error("Error: Reserva recibida sin tipo de cancha reservada.");
            throw new IllegalArgumentException("El tipo de cancha reservada es obligatorio.");
        }

        Complejo complejoAsignado = reserva.getComplejo();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime slotEndTime = reserva.getFechaHora().plusMinutes(SLOT_DURATION_MINUTES);
        if (slotEndTime.isBefore(now)) {
            throw new IllegalArgumentException("No se pueden crear reservas para fechas u horas pasadas.");
        }


        List<Reserva> conflictosExistentes = reservaRepositorio.findConflictingReservationsForPool(
                complejoAsignado.getId(),
                reserva.getTipoCanchaReservada(),
                reserva.getFechaHora(),
                slotEndTime
        );

        Integer totalCanchasDeEsteTipo = complejoAsignado.getCanchaCounts().getOrDefault(reserva.getTipoCanchaReservada(), 0);

        if (conflictosExistentes.size() >= totalCanchasDeEsteTipo) {
            log.warn("Conflicto: Todas las {} canchas de tipo '{}' en complejo '{}' ya están reservadas para el slot {}.",
                    totalCanchasDeEsteTipo, reserva.getTipoCanchaReservada(), complejoAsignado.getNombre(), reserva.getFechaHora());
            throw new IllegalArgumentException("No hay canchas disponibles para el tipo y horario seleccionado en este complejo. Por favor, elige otro.");
        }

        Set<String> nombresCanchasOcupadasEnSlot = conflictosExistentes.stream()
                .map(Reserva::getNombreCanchaAsignada)
                .collect(Collectors.toSet());

        String nombreCanchaAsignada = null;
        for (int i = 1; i <= totalCanchasDeEsteTipo; i++) {
            String posibleNombre = reserva.getTipoCanchaReservada() + " - Instancia " + i;
            if (!nombresCanchasOcupadasEnSlot.contains(posibleNombre)) {
                nombreCanchaAsignada = posibleNombre;
                break;
            }
        }

        if (nombreCanchaAsignada == null) {
            log.error("Error lógico: Se intentó crear una reserva pero no se pudo asignar una instancia de cancha a pesar de que el conteo de disponibilidad indicó que había.");
            throw new IllegalStateException("No se pudo asignar una cancha disponible. Intenta nuevamente.");
        }
        reserva.setNombreCanchaAsignada(nombreCanchaAsignada);

        if (reserva.getPrecio() == null || reserva.getPrecio().compareTo(BigDecimal.ZERO) < 0) {
            log.error("El precio de la reserva es nulo o inválido al intentar guardar.");
            throw new IllegalStateException("Error interno: El precio de la reserva no está definido.");
        }

        if ("efectivo".equalsIgnoreCase(reserva.getMetodoPago())) {
            reserva.setPagada(false);
            reserva.setEstado("pendiente_pago_efectivo");
        } else {
            reserva.setPagada(false);
            reserva.setEstado("pendiente_pago_mp");
        }
        reserva.setFechaPago(null);
        reserva.setMercadoPagoPaymentId(null);

        Reserva reservaGuardada = reservaRepositorio.save(reserva);
        log.info("Reserva creada con ID: {} para complejo '{}' (tipo: '{}'), asignada a: '{}'",
                reservaGuardada.getId(), complejoAsignado.getNombre(), reserva.getTipoCanchaReservada(), nombreCanchaAsignada);

        try {
            emailService.sendNewReservationNotification(reservaGuardada, adminEmail);
            log.info("Notificación de nueva reserva enviada al administrador general ({}).", adminEmail);
        } catch (Exception e) {
            log.error("Error al enviar notificación de nueva reserva al administrador general: {}", e.getMessage(), e);
        }

        if (reserva.getComplejo() != null && reserva.getComplejo().getPropietario() != null) {
            try {
                String ownerEmail = reserva.getComplejo().getPropietario().getUsername();
                emailService.sendNewReservationNotification(reservaGuardada, ownerEmail);
                log.info("Notificación de nueva reserva enviada al dueño del complejo {}.", ownerEmail);
            } catch (Exception e) {
                log.error("Error al enviar notificación de nueva reserva al dueño del complejo: {}", e.getMessage(), e);
            }
        }

        return reservaGuardada;
    }

    @Transactional(readOnly = true)
    public Optional<Reserva> obtenerReserva(Long id) {
        log.info("Buscando reserva con ID: {}", id);
        return reservaRepositorio.findById(id);
    }

    @Transactional
    public Reserva confirmarReserva(Long id, String confirmadorUsername) {
        log.info("Intentando confirmar reserva con ID: {} por usuario: {}", id, confirmadorUsername);
        Reserva r = reservaRepositorio.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada con ID: " + id));

        User confirmador = usuarioRepositorio.findByUsername(confirmadorUsername)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + confirmadorUsername));

        if (confirmador.getRoles().stream().noneMatch(role -> role.getName().equals(ERole.ROLE_ADMIN))) {
            if (r.getComplejo() == null || r.getComplejo().getPropietario() == null || !r.getComplejo().getPropietario().getId().equals(confirmador.getId())) {
                throw new SecurityException("Acceso denegado: No tienes permisos para confirmar esta reserva.");
            }
        }

        if ("pendiente_pago_mp".equalsIgnoreCase(r.getEstado()) || "pendiente".equalsIgnoreCase(r.getEstado())) {
            r.setEstado("confirmada");
        } else if ("pendiente_pago_efectivo".equalsIgnoreCase(r.getEstado())) {
            r.setEstado("confirmada");
        }

        Reserva reservaConfirmada = reservaRepositorio.save(r);
        log.info("Reserva con ID: {} confirmada exitosamente. Nuevo estado: {}", id, reservaConfirmada.getEstado());
        return reservaConfirmada;
    }

    @Transactional
    public void eliminarReserva(Long id, String eliminadorUsername) {
        log.info("Intentando eliminar reserva con ID: {} por usuario: {}", id, eliminadorUsername);
        Reserva r = reservaRepositorio.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada con ID: " + id));

        User eliminador = usuarioRepositorio.findByUsername(eliminadorUsername)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + eliminadorUsername));

        if (eliminador.getRoles().stream().noneMatch(role -> role.getName().equals(ERole.ROLE_ADMIN))) {
            if (r.getComplejo() == null || r.getComplejo().getPropietario() == null || !r.getComplejo().getPropietario().getId().equals(eliminador.getId())) {
                throw new SecurityException("Acceso denegado: No tienes permisos para eliminar esta reserva.");
            }
        }

        reservaRepositorio.deleteById(id);
        log.info("Reserva con ID: {} eliminada exitosamente.", id);
    }

    @Transactional(readOnly = true)
    public List<Reserva> listarTodas(String requesterUsername) {
        log.info("Listando todas las reservas para: {}", requesterUsername);
        User requester = usuarioRepositorio.findByUsername(requesterUsername)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + requesterUsername));

        if (requester.getRoles().stream().anyMatch(r -> r.getName().equals(ERole.ROLE_ADMIN))) {
            log.info("Usuario {} es ADMIN, listando todas las reservas del sistema.", requesterUsername);
            return reservaRepositorio.findAll();
        } else if (requester.getRoles().stream().anyMatch(r -> r.getName().equals(ERole.ROLE_COMPLEX_OWNER))) {
            log.info("Usuario {} es COMPLEX_OWNER, listando reservas de sus complejos.");
            List<Complejo> complejosDelPropietario = complejoRepositorio.findByPropietario(requester);

            if (complejosDelPropietario.isEmpty()) {
                log.warn("El propietario {} no tiene complejos asignados, devolviendo lista de reservas vacía.", requesterUsername);
                return Collections.emptyList();
            }

            List<Long> idsComplejos = complejosDelPropietario.stream()
                    .map(Complejo::getId)
                    .collect(Collectors.toList());

            return reservaRepositorio.findByComplejoIdIn(idsComplejos);
        } else {
            log.warn("Usuario {} no tiene rol de ADMIN o COMPLEX_OWNER para acceder a esta función.", requesterUsername);
            return Collections.emptyList();
        }
    }

    @Transactional(readOnly = true)
    public List<Reserva> obtenerReservasPorUsername(String username) {
        log.debug("Buscando usuario con username/email: {}", username);
        User usuario = usuarioRepositorio.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Usuario no encontrado para obtener reservas: {}", username);
                    return new UsernameNotFoundException("Usuario no encontrado: " + username);
                });
        log.info("Buscando reservas (con usuario y complejo) para Usuario ID: {}", usuario.getId());
        return reservaRepositorio.findByUsuario(usuario);
    }

    @Transactional
    public Reserva marcarComoPagada(Long id, String metodoPago, String mercadoPagoPaymentId, String pagadorUsername) {
        log.info("Intentando marcar como pagada reserva con ID: {} por usuario: {}", id, pagadorUsername);
        Reserva reserva = reservaRepositorio.findById(id).orElseThrow(() -> new RuntimeException("Reserva no encontrada"));

        User pagador = usuarioRepositorio.findByUsername(pagadorUsername)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + pagadorUsername));

        if (pagador.getRoles().stream().noneMatch(role -> role.getName().equals(ERole.ROLE_ADMIN))) {
            if (reserva.getComplejo() == null || reserva.getComplejo().getPropietario() == null || !reserva.getComplejo().getPropietario().getId().equals(pagador.getId())) {
                throw new SecurityException("Acceso denegado: No tienes permisos para marcar esta reserva como pagada.");
            }
        }

        reserva.setPagada(true);
        reserva.setMetodoPago(metodoPago);
        reserva.setMercadoPagoPaymentId(mercadoPagoPaymentId);
        Reserva updatedReserva = reservaRepositorio.save(reserva);
        log.info("Reserva con ID {} marcada como pagada. Nuevo estado: {}", updatedReserva.getId(), updatedReserva.getEstado());
        return updatedReserva;
    }

    @Transactional
    public Reserva generarEquipos(Long id, String generadorUsername) {
        log.info("Generando equipos para reserva ID: {} por usuario: {}", id, generadorUsername);
        Reserva r = reservaRepositorio.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada con ID: " + id));

        User generador = usuarioRepositorio.findByUsername(generadorUsername)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + generadorUsername));

        if (generador.getRoles().stream().noneMatch(role -> role.getName().equals(ERole.ROLE_ADMIN))) {
            if (r.getComplejo() == null || r.getComplejo().getPropietario() == null || !r.getComplejo().getPropietario().getId().equals(generador.getId())) {
                throw new SecurityException("Acceso denegado: No tienes permisos para generar equipos para esta reserva.");
            }
        }

        List<String> jugadores = r.getJugadores();
        if (jugadores == null || jugadores.size() < 2) {
            throw new IllegalArgumentException("Debes ingresar al menos 2 jugadores para armar equipos.");
        }
        Collections.shuffle(jugadores);

        Set<String> equipo1 = new HashSet<>();
        Set<String> equipo2 = new HashSet<>();

        for (int i = 0; i < jugadores.size(); i++) {
            if (i % 2 == 0) {
                equipo1.add(jugadores.get(i));
            } else {
                equipo2.add(jugadores.get(i));
            }
        }
        r.setEquipo1(equipo1);
        r.setEquipo2(equipo2);
        Reserva reservaGuardada = reservaRepositorio.save(r);
        log.info("Equipos generados para reserva ID: {}", id);
        return reservaGuardada;
    }

    @Transactional(readOnly = true)
    public int countAvailableCanchasForSlot(Long complejoId, String tipoCancha, LocalDate fecha, LocalTime hora) {
        log.info("Contando canchas de tipo '{}' disponibles en complejo ID: {} para {} a las {}", tipoCancha, complejoId, fecha, hora);

        Complejo complejo = complejoRepositorio.findById(complejoId)
                .orElseThrow(() -> new IllegalArgumentException("Complejo no encontrado con ID: " + complejoId));

        Integer totalCanchasDeEsteTipo = complejo.getCanchaCounts().getOrDefault(tipoCancha, 0);
        if (totalCanchasDeEsteTipo <= 0) {
            log.debug("Complejo '{}' no tiene canchas de tipo '{}' configuradas o la cantidad es 0.", complejo.getNombre(), tipoCancha);
            return 0;
        }

        LocalDateTime slotStartTime = LocalDateTime.of(fecha, hora);
        LocalDateTime slotEndTime = slotStartTime.plusMinutes(SLOT_DURATION_MINUTES);

        LocalDateTime now = LocalDateTime.now();
        if (slotEndTime.isBefore(now)) {
            log.debug("Slot {}-{} para tipo {} en complejo {} está en el pasado, marcando como 0 disponibles.", slotStartTime.toLocalTime(), slotEndTime.toLocalTime(), tipoCancha, complejoId);
            return 0;
        }

        List<Reserva> conflictos = reservaRepositorio.findConflictingReservationsForPool(
                complejoId,
                tipoCancha,
                slotStartTime,
                slotEndTime
        );

        int bookedCount = conflictos.size();
        int availableCount = totalCanchasDeEsteTipo - bookedCount;

        availableCount = Math.max(0, availableCount);

        log.debug("Encontradas {} canchas de tipo '{}' disponibles en complejo '{}' para {} a las {}. (Total: {}, Reservadas: {})",
                availableCount, tipoCancha, complejo.getNombre(), fecha, hora, totalCanchasDeEsteTipo, bookedCount);
        return availableCount;
    }

    @Transactional(readOnly = true)
    public Optional<String> generateAssignedCanchaName(Long complejoId, String tipoCancha, LocalDate fecha, LocalTime hora) {
        log.info("Generando nombre de cancha asignada para complejo ID: {} tipo: {} en slot: {}", complejoId, tipoCancha, fecha + " " + hora);

        Complejo complejo = complejoRepositorio.findById(complejoId)
                .orElseThrow(() -> new IllegalArgumentException("Complejo no encontrado con ID: " + complejoId));

        Integer totalCanchasDeEsteTipo = complejo.getCanchaCounts().getOrDefault(tipoCancha, 0);
        if (totalCanchasDeEsteTipo <= 0) {
            return Optional.empty();
        }

        LocalDateTime slotStartTime = LocalDateTime.of(fecha, hora);
        LocalDateTime slotEndTime = slotStartTime.plusMinutes(SLOT_DURATION_MINUTES);

        List<Reserva> conflictos = reservaRepositorio.findConflictingReservationsForPool(
                complejoId,
                tipoCancha,
                slotStartTime,
                slotEndTime
        );

        Set<String> nombresCanchasOcupadasEnSlot = conflictos.stream()
                .map(Reserva::getNombreCanchaAsignada)
                .collect(Collectors.toSet());

        for (int i = 1; i <= totalCanchasDeEsteTipo; i++) {
            String posibleNombre = tipoCancha + " - Instancia " + i;
            if (!nombresCanchasOcupadasEnSlot.contains(posibleNombre)) {
                log.debug("Nombre de instancia asignado: {}", posibleNombre);
                return Optional.of(posibleNombre);
            }
        }
        log.debug("Todas las canchas de tipo '{}' en complejo '{}' están ocupadas para el slot {}.", tipoCancha, complejo.getNombre(), fecha + " " + hora);
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public EstadisticasResponse calcularEstadisticas(String requesterUsername) {
        log.info("Calculando estadísticas para: {}", requesterUsername);
        User requester = usuarioRepositorio.findByUsername(requesterUsername)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + requesterUsername));

        List<Reserva> reservasParaEstadisticas;

        if (requester.getRoles().stream().anyMatch(r -> r.getName().equals(ERole.ROLE_ADMIN))) {
            log.info("Generando estadísticas globales (ADMIN).");
            reservasParaEstadisticas = reservaRepositorio.findAll();
        } else if (requester.getRoles().stream().anyMatch(r -> r.getName().equals(ERole.ROLE_COMPLEX_OWNER))) {
            log.info("Generando estadísticas para complejos de propietario {}.", requesterUsername);
            List<Complejo> complejosDelPropietario = complejoRepositorio.findByPropietario(requester);

            if (complejosDelPropietario.isEmpty()) {
                log.warn("Propietario {} no tiene complejos, no hay estadísticas para mostrar.", requesterUsername);
                // Devuelve un EstadisticasResponse vacío en lugar de una lista vacía
                return new EstadisticasResponse(
                        BigDecimal.ZERO, 0L, 0L, 0L, new HashMap<>(), new HashMap<>()
                );
            }
            List<Long> idsComplejos = complejosDelPropietario.stream()
                    .map(Complejo::getId)
                    .collect(Collectors.toList());
            reservasParaEstadisticas = reservaRepositorio.findByComplejoIdIn(idsComplejos);
        } else {
            log.warn("Usuario {} no tiene rol de ADMIN o COMPLEX_OWNER para ver estadísticas.", requesterUsername);
            // Devuelve un EstadisticasResponse vacío en lugar de una lista vacía
            return new EstadisticasResponse(
                    BigDecimal.ZERO, 0L, 0L, 0L, new HashMap<>(), new HashMap<>()
            );
        }

        BigDecimal ingresosTotalesConfirmados = reservasParaEstadisticas.stream()
                .filter(reserva -> "pagada".equalsIgnoreCase(reserva.getEstado()))
                .map(Reserva::getPrecio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Long totalReservasConfirmadas = reservasParaEstadisticas.stream()
                .filter(reserva -> "pagada".equalsIgnoreCase(reserva.getEstado()) || "confirmada".equalsIgnoreCase(reserva.getEstado()) || "pendiente_pago_efectivo".equalsIgnoreCase(reserva.getEstado()))
                .count();

        Long totalReservasPendientes = reservasParaEstadisticas.stream()
                .filter(reserva -> "pendiente".equalsIgnoreCase(reserva.getEstado()) || "pendiente_pago_mp".equalsIgnoreCase(reserva.getEstado()))
                .count();

        Long totalReservasCanceladas = reservasParaEstadisticas.stream()
                .filter(reserva -> "cancelada".equalsIgnoreCase(reserva.getEstado()) || "rechazada_pago_mp".equalsIgnoreCase(reserva.getEstado()))
                .count();

        Map<String, Long> reservasPorTipoCancha = reservasParaEstadisticas.stream()
                .collect(Collectors.groupingBy(Reserva::getTipoCanchaReservada, Collectors.counting()));

        Map<String, Long> horariosPico = reservasParaEstadisticas.stream()
                .map(reserva -> reserva.getFechaHora().toLocalTime().truncatedTo(ChronoUnit.HOURS))
                .collect(Collectors.groupingBy(LocalTime::toString, Collectors.counting()));

        return new EstadisticasResponse(
                ingresosTotalesConfirmados,
                totalReservasConfirmadas,
                totalReservasPendientes,
                totalReservasCanceladas,
                reservasPorTipoCancha,
                horariosPico
        );
    }
}