package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Modelo.Cancha; // Importar Cancha
import com.example.reservafutbol.Modelo.Reserva;
import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Repositorio.CanchaRepositorio; // Importar CanchaRepositorio
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
import java.util.Comparator; // Importar Comparator
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReservaServicio {

    private static final Logger log = LoggerFactory.getLogger(ReservaServicio.class);

    @Autowired
    private ReservaRepositorio reservaRepositorio;

    @Autowired
    private UsuarioRepositorio usuarioRepositorio;

    @Autowired
    private CanchaRepositorio canchaRepositorio; // Inyectar CanchaRepositorio

    @Autowired
    private EmailService emailService;

    @Value("${admin.email}")
    private String adminEmail;

    private static final int SLOT_DURATION_MINUTES = 60;
    private static final LocalTime OPEN_TIME = LocalTime.of(10, 0); // ABIERTO DE 10:00
    // Si el cierre es exactamente a las 00:00 (medianoche), significa que el último slot reservable es 23:00 - 00:00.
    // Por lo tanto, el `CLOSE_TIME` para generar slots debería ser 23:00 si los slots son de 1 hora y terminan a medianoche.
    // Si realmente se puede reservar a las 00:00, entonces el `CLOSE_TIME` puede ser 00:00 y el bucle ajustarse.
    // Para 10:00 a 00:00, los slots serían 10:00, 11:00, ..., 23:00.
    private static final LocalTime LAST_BOOKABLE_HOUR_START = LocalTime.of(23, 0); // Último slot que comienza a las 23:00

    // Método anterior (listarReservas por canchaId) se mantiene para compatibilidad, pero el flujo principal ya no lo usa para crear
    public List<Reserva> listarReservas(Long canchaId) {
        log.info("Buscando reservas para cancha ID: {}", canchaId);
        return reservaRepositorio.findByCanchaId(canchaId);
    }

    @Transactional
    // MODIFICADO: `crearReserva` ahora espera que la cancha ya esté asignada por el controlador.
    // La asignación de la cancha (`findFirstAvailableCancha`) ocurre *antes* de llamar a este método.
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

        // VALIDACIÓN CRÍTICA: Asegurarse de que el objeto Cancha ya esté asignado en la reserva
        if (reserva.getCancha() == null || reserva.getCancha().getId() == null) {
            log.error("Error: Reserva recibida sin una cancha asignada. El controlador debe asignar la cancha antes de llamar a crearReserva.");
            throw new IllegalStateException("Error interno al procesar la reserva: cancha no asignada.");
        }

        // Ya que la cancha asignada viene en 'reserva.cancha', se usan sus detalles.
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
        // Se realiza para la CANCHA ESPECÍFICA que ha sido asignada.
        LocalDateTime slotEndTime = reserva.getFechaHora().plusMinutes(SLOT_DURATION_MINUTES);
        List<Reserva> conflictos = reservaRepositorio.findConflictingReservations(
                canchaAsignada.getId(), // Usamos el ID de la cancha que el sistema asignó
                reserva.getFechaHora(),
                slotEndTime
        );

        if (!conflictos.isEmpty()) {
            log.warn("Conflicto de reserva detectado para cancha {} en slot {}. Conflictos: {}", canchaAsignada.getId(), reserva.getFechaHora(), conflictos.stream().map(r -> r.getId().toString()).collect(Collectors.joining(",")));
            throw new IllegalArgumentException("El horario seleccionado ya está reservado o se solapa con otra reserva. Por favor, elige otro horario.");
        }

        // Asignar el nombre de la cancha a la reserva (para auditoría y DTOs de salida)
        reserva.setCanchaNombre(canchaAsignada.getNombre());
        // Asignar el precio de la cancha (se asume que ya viene del DTO o del controlador)
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
        } else { // Para Mercado Pago
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

        // Simplificar la lógica de confirmación: solo si no está ya pagada o cancelada
        if (!"pagada".equalsIgnoreCase(r.getEstado()) && !"cancelada".equalsIgnoreCase(r.getEstado()) && !"rechazada_pago_mp".equalsIgnoreCase(r.getEstado())) {
            r.setConfirmada(true);
            // Si el estado era 'pendiente_pago_mp', al confirmar manualmente, podría pasar a 'confirmada' o 'pendiente_pago_efectivo' si se convierte.
            // Lo más limpio es que Mercado Pago actualice el estado a 'pagada'. Si el admin la "confirma" manualmente,
            // podría ser que está aceptando que se pague en efectivo.
            if ("pendiente_pago_mp".equalsIgnoreCase(r.getEstado())) {
                r.setEstado("confirmada"); // Si el admin la confirma antes que MP, se convierte en 'confirmada'
            } else if ("pendiente".equalsIgnoreCase(r.getEstado())) { // Si estaba en estado genérico 'pendiente'
                r.setEstado("confirmada");
            }
            // Si es 'pendiente_pago_efectivo', ya está confirmada al crear, no hace falta cambiar estado aquí.

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
        // El estado se actualizará vía @PreUpdate, o puedes hacerlo explícitamente aquí:
        // reserva.setEstado("pagada");
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

    // --- NUEVO MÉTODO: Contar canchas disponibles por tipo para un slot dado ---
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

    // NUEVO MÉTODO: Obtener la primera cancha disponible de un tipo para un slot dado
    // Este método es usado INTERNAMENTE por el controlador/servicio para asignar la cancha.
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

        // Opcional: ordenar las canchas si hay una preferencia (ej. por ID para consistencia en la asignación)
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

    // El método `getAvailableTimeSlots(Long canchaId, LocalDate fecha)`
    // que devuelve una `List<String>` de horas disponibles para una cancha específica,
    // se mantiene aquí. Es usado por `ReservaForm.jsx` si el frontend *aún permite*
    // reservar por `canchaId`. Si la idea es que el frontend *solo* use el flujo
    // de tipo de cancha, este método ya no sería llamado por `ReservaForm`.
    // Sin embargo, como el endpoint `/reservar/:canchaId` aún existe en `App.js`,
    // y el `ReservaForm` lo sigue usando con `useParams`, es necesario mantenerlo.
    // Para el flujo de "pool de canchas", el `ReservaForm` ya no recibirá `canchaId`
    // y no usará este método. Se usarán `countAvailableCanchasForSlot` y `findFirstAvailableCancha`.
    @Transactional(readOnly = true)
    public List<String> getAvailableTimeSlots(Long canchaId, LocalDate fecha) {
        log.info("Consultando slots disponibles para cancha ID: {} en fecha: {}", canchaId, fecha);

        List<String> allPossibleSlots = new ArrayList<>();
        LocalTime currentTime = OPEN_TIME;
        // La condición para el bucle: mientras la hora actual sea antes de la última hora de inicio de slot
        while (currentTime.isBefore(LAST_BOOKABLE_HOUR_START) || currentTime.equals(LAST_BOOKABLE_HOUR_START)) {
            allPossibleSlots.add(currentTime.toString());
            currentTime = currentTime.plusMinutes(SLOT_DURATION_MINUTES);
        }

        List<Reserva> occupiedReservations = reservaRepositorio.findOccupiedSlotsByCanchaAndDate(canchaId, fecha);

        Set<String> occupiedSlots = occupiedReservations.stream()
                .map(reserva -> reserva.getFechaHora().toLocalTime().truncatedTo(ChronoUnit.HOURS).toString())
                .collect(Collectors.toSet());

        List<String> availableSlots = allPossibleSlots.stream()
                .filter(slot -> !occupiedSlots.contains(slot))
                .collect(Collectors.toList());

        if (fecha.isEqual(LocalDate.now())) {
            LocalTime nowTime = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
            availableSlots = availableSlots.stream()
                    .filter(slot -> LocalTime.parse(slot).isAfter(nowTime))
                    .collect(Collectors.toList());
        }
        log.debug("Slots disponibles para cancha {} en {}: {}", canchaId, fecha, availableSlots);
        return availableSlots;
    }
}