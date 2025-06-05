package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Modelo.Cancha;
import com.example.reservafutbol.Modelo.Reserva;
import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Repositorio.CanchaRepositorio;
import com.example.reservafutbol.Repositorio.ReservaRepositorio;
import com.example.reservafutbol.Repositorio.UsuarioRepositorio;
import com.example.reservafutbol.payload.response.EstadisticasResponse; // Asegúrate de que este DTO exista
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

// Importar ZoneId para manejo de zonas horarias si fuera necesario
// import java.time.ZoneId;

@Service
public class ReservaServicio {

    private static final Logger log = LoggerFactory.getLogger(ReservaServicio.class);

    @Autowired
    private ReservaRepositorio reservaRepositorio;

    @Autowired
    private UsuarioRepositorio usuarioRepositorio;

    @Autowired
    private CanchaRepositorio canchaRepositorio;

    @Autowired
    private EmailService emailService;

    @Value("${admin.email}")
    private String adminEmail;

    private static final int SLOT_DURATION_MINUTES = 60;
    private static final LocalTime OPEN_TIME = LocalTime.of(10, 0); // Abre a las 10:00 AM
    private static final LocalTime LAST_BOOKABLE_HOUR_START = LocalTime.of(23, 0); // Último slot que comienza a las 23:00 (para terminar a las 00:00)

    // Método anterior (listarReservas por canchaId) se mantiene para compatibilidad
    public List<Reserva> listarReservas(Long canchaId) {
        log.info("Buscando reservas para cancha ID: {}", canchaId);
        return reservaRepositorio.findByCanchaId(canchaId);
    }

    @Transactional
    public Reserva crearReserva(Reserva reserva) {
        // Validaciones previas que ya tenías
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

        // VALIDACIÓN CRÍTICA: Asegurarse de que el objeto Cancha ya esté asignado en la reserva por el controlador
        if (reserva.getCancha() == null || reserva.getCancha().getId() == null) {
            log.error("Error: Reserva recibida sin una cancha asignada. El controlador debe asignar la cancha antes de llamar a crearReserva.");
            throw new IllegalStateException("Error interno al procesar la reserva: cancha no asignada.");
        }

        Cancha canchaAsignada = reserva.getCancha();

        // Validar si la cancha asignada está disponible (estado general)
        if (Boolean.FALSE.equals(canchaAsignada.getDisponible())) {
            throw new IllegalArgumentException("La cancha asignada ('" + canchaAsignada.getNombre() + "') no está disponible para reservas en este momento.");
        }

        // Validación de fecha y hora futura
        LocalDateTime now = LocalDateTime.now();
        if (reserva.getFechaHora().isBefore(now)) {
            throw new IllegalArgumentException("No se pueden crear reservas para fechas u horas pasadas.");
        }

        // --- VALIDACIÓN FINAL DE DISPONIBILIDAD DEL SLOT (Anti-doble reserva) ---
        LocalDateTime slotEndTime = reserva.getFechaHora().plusMinutes(SLOT_DURATION_MINUTES);
        List<Reserva> conflictos = reservaRepositorio.findConflictingReservations(
                canchaAsignada.getId(),
                reserva.getFechaHora(),
                slotEndTime
        );

        if (!conflictos.isEmpty()) {
            log.warn("Conflicto de reserva detectado para cancha {} en slot {}. Conflictos: {}", canchaAsignada.getId(), reserva.getFechaHora(), conflictos.stream().map(r -> r.getId().toString()).collect(Collectors.joining(",")));
            throw new IllegalArgumentException("El horario seleccionado ya está reservado o se solapa con otra reserva. Por favor, elige otro horario.");
        }

        reserva.setCanchaNombre(canchaAsignada.getNombre());
        if (canchaAsignada.getPrecioPorHora() != null) {
            reserva.setPrecio(BigDecimal.valueOf(canchaAsignada.getPrecioPorHora()));
        } else {
            log.error("La cancha asignada {} (ID: {}) no tiene precio por hora definido.", canchaAsignada.getNombre(), canchaAsignada.getId());
            throw new IllegalStateException("Error interno: El precio de la cancha no está definido.");
        }

        // --- ASIGNACIÓN DE ESTADO INICIAL ---
        if ("efectivo".equalsIgnoreCase(reserva.getMetodoPago())) {
            reserva.setConfirmada(true);
            reserva.setPagada(false);
            reserva.setEstado("pendiente_pago_efectivo");
        } else {
            reserva.setConfirmada(true);
            reserva.setPagada(false);
            reserva.setEstado("pendiente_pago_mp");
        }
        reserva.setFechaPago(null);
        reserva.setMercadoPagoPaymentId(null);

        Reserva reservaGuardada = reservaRepositorio.save(reserva);
        log.info("Reserva creada con ID: {} para cancha {} (tipo: {})", reservaGuardada.getId(), canchaAsignada.getNombre(), canchaAsignada.getTipoCancha());

        // --- NOTIFICACIÓN AL DUEÑO ---
        try {
            emailService.sendNewReservationNotification(reservaGuardada, adminEmail);
            log.info("Notificación de nueva reserva enviada al administrador.");
        } catch (Exception e) {
            log.error("Error al enviar notificación de nueva reserva al administrador: {}", e.getMessage(), e);
        }

        return reservaGuardada;
    }

    @Transactional(readOnly = true)
    public Optional<Reserva> obtenerReserva(Long id) {
        log.info("Buscando reserva con ID: {}", id);
        return reservaRepositorio.findById(id);
    }

    @Transactional
    public Reserva confirmarReserva(Long id) {
        log.info("Intentando confirmar reserva con ID: {}", id);
        Reserva r = reservaRepositorio.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada con ID: " + id));

        if (!"pagada".equalsIgnoreCase(r.getEstado()) && !"cancelada".equalsIgnoreCase(r.getEstado()) && !"rechazada_pago_mp".equalsIgnoreCase(r.getEstado())) {
            r.setConfirmada(true);
            if ("pendiente_pago_mp".equalsIgnoreCase(r.getEstado())) {
                r.setEstado("confirmada");
            } else if ("pendiente".equalsIgnoreCase(r.getEstado())) {
                r.setEstado("confirmada");
            }

            Reserva reservaConfirmada = reservaRepositorio.save(r);
            log.info("Reserva con ID: {} confirmada exitosamente.", id);
            return reservaConfirmada;
        } else {
            log.warn("No se pudo confirmar la reserva con ID: {} porque su estado actual es '{}'.", id, r.getEstado());
            throw new IllegalStateException("La reserva no puede ser confirmada en su estado actual: ya está pagada/cancelada.");
        }
    }

    @Transactional
    public void eliminarReserva(Long id) {
        log.info("Intentando eliminar reserva con ID: {}", id);
        if (reservaRepositorio.existsById(id)) {
            reservaRepositorio.deleteById(id);
            log.info("Reserva con ID: {} eliminada exitosamente.", id);
        } else {
            throw new IllegalArgumentException("Reserva no encontrada con ID: " + id);
        }
    }

    @Transactional(readOnly = true)
    public List<Reserva> listarTodas() {
        log.info("Listando todas las reservas (para admin).");
        return reservaRepositorio.findAll();
    }

    @Transactional(readOnly = true)
    public List<Reserva> obtenerReservasPorUsername(String username) {
        log.debug("Buscando usuario con username/email: {}", username);
        User usuario = usuarioRepositorio.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Usuario no encontrado para obtener reservas: {}", username);
                    return new UsernameNotFoundException("Usuario no encontrado: " + username);
                });
        log.info("Buscando reservas (con usuario y cancha) para Usuario ID: {}", usuario.getId());
        return reservaRepositorio.findByUsuario(usuario);
    }

    @Transactional
    public Reserva marcarComoPagada(Long id, String metodoPago, String mercadoPagoPaymentId) {
        Reserva reserva = reservaRepositorio.findById(id).orElseThrow(() -> new RuntimeException("Reserva no encontrada"));
        reserva.setPagada(true);
        reserva.setMetodoPago(metodoPago);
        reserva.setMercadoPagoPaymentId(mercadoPagoPaymentId);
        return reservaRepositorio.save(reserva);
    }

    @Transactional
    public Reserva generarEquipos(Long id) {
        log.info("Generando equipos para reserva ID: {}", id);
        Reserva r = reservaRepositorio.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada con ID: " + id));
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

    // --- MÉTODO: Contar canchas disponibles por tipo para un slot dado ---
    @Transactional(readOnly = true)
    public int countAvailableCanchasForSlot(String tipoCancha, LocalDate fecha, LocalTime hora) {
        log.info("Contando canchas de tipo '{}' disponibles para {} a las {}", tipoCancha, fecha, hora);

        List<Cancha> canchasDelTipo = canchaRepositorio.findByTipoCanchaAndDisponibleTrue(tipoCancha);
        if (canchasDelTipo.isEmpty()) {
            log.debug("No hay canchas de tipo '{}' marcadas como disponibles.", tipoCancha);
            return 0;
        }

        LocalDateTime slotStartTime = LocalDateTime.of(fecha, hora);
        LocalDateTime slotEndTime = slotStartTime.plusMinutes(SLOT_DURATION_MINUTES);

        int availableCount = 0;

        for (Cancha cancha : canchasDelTipo) {
            List<Reserva> conflictos = reservaRepositorio.findConflictingReservations(
                    cancha.getId(),
                    slotStartTime,
                    slotEndTime
            );
            if (conflictos.isEmpty()) {
                availableCount++;
            }
        }
        log.debug("Encontradas {} canchas de tipo '{}' disponibles para {} a las {}", availableCount, tipoCancha, fecha, hora);
        return availableCount;
    }

    // --- MÉTODO: Obtener la primera cancha disponible de un tipo para un slot dado ---
    @Transactional(readOnly = true)
    public Optional<Cancha> findFirstAvailableCancha(String tipoCancha, LocalDate fecha, LocalTime hora) {
        log.info("Buscando la primera cancha disponible de tipo '{}' para {} a las {}", tipoCancha, fecha, hora);

        List<Cancha> canchasDelTipo = canchaRepositorio.findByTipoCanchaAndDisponibleTrue(tipoCancha);
        if (canchasDelTipo.isEmpty()) {
            log.debug("No hay canchas de tipo '{}' marcadas como disponibles para asignar.", tipoCancha);
            return Optional.empty();
        }

        LocalDateTime slotStartTime = LocalDateTime.of(fecha, hora);
        LocalDateTime slotEndTime = slotStartTime.plusMinutes(SLOT_DURATION_MINUTES);

        canchasDelTipo.sort(Comparator.comparing(Cancha::getId));

        for (Cancha cancha : canchasDelTipo) {
            List<Reserva> conflictos = reservaRepositorio.findConflictingReservations(
                    cancha.getId(),
                    slotStartTime,
                    slotEndTime
            );
            if (conflictos.isEmpty()) {
                log.debug("Cancha '{}' (ID: {}) encontrada disponible de tipo '{}' para {} a las {}. Asignando.", cancha.getNombre(), cancha.getId(), tipoCancha, fecha, hora);
                return Optional.of(cancha);
            }
        }
        log.debug("No se encontró ninguna cancha de tipo '{}' disponible para {} a las {}. Todas ocupadas.", tipoCancha, fecha, hora);
        return Optional.empty();
    }

    // --- MÉTODO: getAvailableTimeSlots (Para canchas específicas) ---
    // Este método se mantiene para compatibilidad con el frontend si aún permite reservar por canchaId.
    @Transactional(readOnly = true)
    public List<String> getAvailableTimeSlots(Long canchaId, LocalDate fecha) {
        log.info("Consultando slots disponibles para cancha ID: {} en fecha: {}", canchaId, fecha);

        List<String> allPossibleSlots = new ArrayList<>();
        LocalTime currentTime = OPEN_TIME;
        while (currentTime.isBefore(LAST_BOOKABLE_HOUR_START) || currentTime.equals(LAST_BOOKABLE_HOUR_START)) {
            allPossibleSlots.add(currentTime.toString());
            currentTime = currentTime.plusMinutes(SLOT_DURATION_MINUTES);
        }

        // ¡¡CORRECCIÓN AQUÍ: EL NOMBRE DEL MÉTODO EN EL REPOSITORIO ES findOccupiedSlotsByCanchaAndDate!!
        List<Reserva> occupiedReservations = reservaRepositorio.findOccupiedSlotsByCanchaAndDate(canchaId, fecha);

        Set<String> occupiedSlots = occupiedReservations.stream()
                .map(reserva -> reserva.getFechaHora().toLocalTime().truncatedTo(ChronoUnit.HOURS).toString())
                .collect(Collectors.toSet());

        List<String> availableSlots = allPossibleSlots.stream()
                .filter(slot -> !occupiedSlots.contains(slot))
                .collect(Collectors.toList());

        // La hora local en San Martín, Mendoza, Argentina.
        // Considerando el horario de verano/invierno, pero para simplificar, usaremos la hora actual del sistema.
        // Si necesitas una zona horaria específica, usa:
        // ZoneId argentinaZone = ZoneId.of("America/Argentina/Mendoza");
        // LocalTime nowTime = LocalTime.now(argentinaZone).truncatedTo(ChronoUnit.MINUTES);
        LocalTime nowTime = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);


        if (fecha.isEqual(LocalDate.now())) {
            availableSlots = availableSlots.stream()
                    .filter(slot -> LocalTime.parse(slot).isAfter(nowTime))
                    .collect(Collectors.toList());
        }
        log.debug("Slots disponibles para cancha {} en {}: {}", canchaId, fecha, availableSlots);
        return availableSlots;
    }

    // --- Método calcularEstadisticas (Reincorporado y corregido en el último envío) ---
    @Transactional(readOnly = true)
    public EstadisticasResponse calcularEstadisticas() {
        log.info("Calculando estadísticas del complejo...");
        List<Reserva> todasLasReservas = reservaRepositorio.findAll();

        BigDecimal ingresosTotalesConfirmados = todasLasReservas.stream()
                .filter(reserva -> "pagada".equalsIgnoreCase(reserva.getEstado()))
                .map(Reserva::getPrecio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        log.debug("Ingresos totales confirmados y pagados: {}", ingresosTotalesConfirmados);


        Long totalReservasConfirmadas = todasLasReservas.stream()
                .filter(reserva -> reserva.getEstado().startsWith("confirmada") || "pagada".equalsIgnoreCase(reserva.getEstado()))
                .count();

        Long totalReservasPendientes = todasLasReservas.stream()
                .filter(reserva -> "pendiente".equalsIgnoreCase(reserva.getEstado()) || "pendiente_pago_mp".equalsIgnoreCase(reserva.getEstado()))
                .count();

        Long totalReservasCanceladas = todasLasReservas.stream()
                .filter(reserva -> "cancelada".equalsIgnoreCase(reserva.getEstado()) || "rechazada_pago_mp".equalsIgnoreCase(reserva.getEstado()))
                .count();
        log.debug("Reservas: Confirmadas={}, Pendientes={}, Canceladas={}",
                totalReservasConfirmadas, totalReservasPendientes, totalReservasCanceladas);


        Map<String, Long> reservasPorCancha = todasLasReservas.stream()
                .collect(Collectors.groupingBy(Reserva::getCanchaNombre, Collectors.counting()));
        log.debug("Reservas por cancha: {}", reservasPorCancha);

        Map<String, Long> horariosPico = todasLasReservas.stream()
                .map(reserva -> reserva.getFechaHora().toLocalTime().truncatedTo(ChronoUnit.HOURS))
                .collect(Collectors.groupingBy(LocalTime::toString, Collectors.counting()));
        log.debug("Horarios pico: {}", horariosPico);


        return new EstadisticasResponse(
                ingresosTotalesConfirmados,
                totalReservasConfirmadas,
                totalReservasPendientes,
                totalReservasCanceladas,
                reservasPorCancha,
                horariosPico
        );
    }
}