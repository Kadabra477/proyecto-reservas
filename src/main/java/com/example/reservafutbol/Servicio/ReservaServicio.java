package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Modelo.Cancha;
import com.example.reservafutbol.Modelo.Reserva;
import com.example.reservafutbol.Modelo.User;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class ReservaServicio {

    private static final Logger log = LoggerFactory.getLogger(ReservaServicio.class); // Declaración correcta del logger

    @Autowired
    private ReservaRepositorio reservaRepositorio;

    @Autowired
    private UsuarioRepositorio usuarioRepositorio;

    @Autowired
    private EmailService emailService;

    @Value("${admin.email}")
    private String adminEmail;

    private static final int SLOT_DURATION_MINUTES = 60;
    private static final LocalTime OPEN_TIME = LocalTime.of(8, 0);
    private static final LocalTime CLOSE_TIME = LocalTime.of(22, 0);

    public List<Reserva> listarReservas(Long canchaId) {
        log.info("Buscando reservas para cancha ID: {}", canchaId);
        return reservaRepositorio.findByCanchaId(canchaId);
    }

    @Transactional
    public Reserva crearReserva(Reserva reserva) {
        log.info("Intentando crear reserva para cancha ID: {} por usuario: {} en fecha/hora: {} con método de pago: {}",
                reserva.getCancha() != null ? reserva.getCancha().getId() : "null",
                reserva.getUsuario() != null ? reserva.getUsuario().getUsername() : reserva.getUserEmail(),
                reserva.getFechaHora(),
                reserva.getMetodoPago());

        if (reserva.getUsuario() == null && (reserva.getUserEmail() == null || reserva.getUserEmail().isBlank())) {
            throw new IllegalArgumentException("La reserva debe estar asociada a un usuario (email).");
        }
        if (reserva.getCancha() == null) {
            throw new IllegalArgumentException("La cancha es obligatoria.");
        }
        if (reserva.getCancha().getNombre() != null) {
            reserva.setCanchaNombre(reserva.getCancha().getNombre());
        } else {
            throw new IllegalArgumentException("La cancha debe tener un nombre.");
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

        if (Boolean.FALSE.equals(reserva.getCancha().getDisponible())) {
            throw new IllegalArgumentException("Esta cancha no está disponible para reservas en este momento.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (reserva.getFechaHora().isBefore(now)) {
            throw new IllegalArgumentException("No se pueden crear reservas para fechas u horas pasadas.");
        }

        // --- NUEVA VALIDACIÓN DE DISPONIBILIDAD DE SLOT (Anti-doble reserva) ---
        LocalDateTime slotEndTime = reserva.getFechaHora().plusMinutes(SLOT_DURATION_MINUTES);

        List<Reserva> conflictos = reservaRepositorio.findConflictingReservations(
                reserva.getCancha().getId(),
                reserva.getFechaHora(),
                slotEndTime
        );

        if (!conflictos.isEmpty()) {
            throw new IllegalArgumentException("El horario seleccionado ya está reservado o se solapa con otra reserva. Por favor, elige otro horario.");
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
        log.info("Reserva creada con ID: {}", reservaGuardada.getId());

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

        if ("pendiente".equalsIgnoreCase(r.getEstado()) || "pendiente_pago_mp".equalsIgnoreCase(r.getEstado())) { // Permite confirmar pendientes
            r.setConfirmada(true);
            // Si es MP y ya está pagada (por webhook), no sobrescribir a 'confirmada', dejar 'pagada'.
            // Si es efectivo pendiente, cambiar a 'confirmada_efectivo'.
            if ("pendiente_pago_mp".equalsIgnoreCase(r.getEstado())) {
                // Si estaba pendiente de pago MP y el admin la confirma, es una confirmación manual.
                // Podríamos considerar un estado intermedio o dejar que el webhook la marque como pagada.
                // Para este flujo simple, si el admin la confirma manualmente, la dejamos en 'confirmada'.
                // Lo ideal sería que el webhook de MP cambie directamente a 'pagada'.
                r.setEstado("confirmada"); // Si el admin la confirma antes que MP, se convierte en 'confirmada'
            } else if ("pendiente".equalsIgnoreCase(r.getEstado())) {
                // Este estado 'pendiente' general se usaba si no se especificaba método de pago inicialmente.
                // Ahora con los métodos de pago, este caso debería ser menos común.
                r.setEstado("confirmada");
            }
            // Si el estado es 'pendiente_pago_efectivo', ya se marcó confirmada al crear, no hace falta aquí.

            Reserva reservaConfirmada = reservaRepositorio.save(r);
            log.info("Reserva con ID: {} confirmada exitosamente.", id);
            return reservaConfirmada;
        } else {
            log.warn("No se pudo confirmar la reserva con ID: {} porque su estado actual es '{}'.", id, r.getEstado());
            throw new IllegalStateException("La reserva no puede ser confirmada en su estado actual.");
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
        // El estado se actualizará vía @PreUpdate
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

    // --- NUEVO MÉTODO: Obtener Slots de Tiempo Disponibles para una Cancha y Fecha ---
    @Transactional(readOnly = true)
    public List<String> getAvailableTimeSlots(Long canchaId, LocalDate fecha) {
        log.info("Consultando slots disponibles para cancha ID: {} en fecha: {}", canchaId, fecha);

        List<String> allPossibleSlots = new ArrayList<>();
        LocalTime currentTime = OPEN_TIME;
        while (currentTime.isBefore(CLOSE_TIME) || currentTime.equals(CLOSE_TIME)) {
            allPossibleSlots.add(currentTime.toString());
            currentTime = currentTime.plusMinutes(SLOT_DURATION_MINUTES);
            if (currentTime.isAfter(CLOSE_TIME) && !currentTime.minusMinutes(SLOT_DURATION_MINUTES).equals(CLOSE_TIME)) {
                break;
            }
        }

        // CORRECCIÓN: Orden de los parámetros en la llamada al repositorio
        List<Reserva> occupiedReservations = reservaRepositorio.findOccupiedSlotsByCanchaAndDate(canchaId, fecha);

        Set<String> occupiedSlots = occupiedReservations.stream()
                .map(reserva -> reserva.getFechaHora().toLocalTime().truncatedTo(ChronoUnit.HOURS).toString())
                .collect(Collectors.toSet());

        List<String> availableSlots = allPossibleSlots.stream()
                .filter(slot -> !occupiedSlots.contains(slot))
                .collect(Collectors.toList());

        // Filtrar slots pasados si la fecha es hoy
        if (fecha.isEqual(LocalDate.now())) {
            LocalTime nowTime = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
            availableSlots = availableSlots.stream()
                    .filter(slot -> LocalTime.parse(slot).isAfter(nowTime))
                    .collect(Collectors.toList());
        }

        log.debug("Slots disponibles para cancha {} en {}: {}", canchaId, fecha, availableSlots);
        return availableSlots;
    }

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