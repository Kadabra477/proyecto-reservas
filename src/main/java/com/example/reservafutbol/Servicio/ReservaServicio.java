package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Modelo.Complejo; // Importar Complejo
import com.example.reservafutbol.Modelo.Reserva;
import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Repositorio.ComplejoRepositorio; // Importar ComplejoRepositorio
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
    private ComplejoRepositorio complejoRepositorio; // Inyectar ComplejoRepositorio

    @Autowired
    private EmailService emailService;

    @Value("${admin.email}")
    private String adminEmail;

    private static final int SLOT_DURATION_MINUTES = 60;
    // Horarios de apertura y cierre por defecto. Idealmente, estos vendrían del Complejo.
    private static final LocalTime DEFAULT_OPEN_TIME = LocalTime.of(10, 0); // 10:00 AM
    private static final LocalTime DEFAULT_LAST_BOOKABLE_HOUR_START = LocalTime.of(23, 0); // Último slot comienza a las 23:00

    // Método `listarReservas` (por ID de Complejo, no de cancha específica)
    public List<Reserva> listarReservasPorComplejo(Long complejoId) {
        log.info("Buscando reservas para complejo ID: {}", complejoId);
        // Aquí necesitaríamos un método en ReservaRepositorio como findByComplejoId
        // Por ahora, usaremos findAll() y filtraremos en Java, o crearemos el método si es necesario.
        return reservaRepositorio.findAll().stream()
                .filter(reserva -> reserva.getComplejo() != null && reserva.getComplejo().getId().equals(complejoId))
                .collect(Collectors.toList());
    }

    @Transactional
    // MODIFICADO: `crearReserva` ahora espera que la reserva ya tenga el Complejo asignado.
    // La asignación de la "instancia de cancha" (ej. "Fútbol 5 - Instancia 3") se hace aquí o en el controlador.
    public Reserva crearReserva(Reserva reserva) {
        // Validaciones básicas
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

        // VALIDACIÓN CRÍTICA: Asegurarse de que el objeto Complejo ya esté asignado en la reserva por el controlador
        if (reserva.getComplejo() == null || reserva.getComplejo().getId() == null) {
            log.error("Error: Reserva recibida sin un complejo asignado. El controlador debe asignar el complejo antes de llamar a crearReserva.");
            throw new IllegalStateException("Error interno al procesar la reserva: complejo no asignado.");
        }
        // VALIDACIÓN CRÍTICA: Asegurarse de que el tipo de cancha reservado exista
        if (reserva.getTipoCanchaReservada() == null || reserva.getTipoCanchaReservada().isBlank()) {
            log.error("Error: Reserva recibida sin tipo de cancha reservada.");
            throw new IllegalArgumentException("El tipo de cancha reservada es obligatorio.");
        }

        Complejo complejoAsignado = reserva.getComplejo();

        // Validación de fecha y hora futura
        LocalDateTime now = LocalDateTime.now();
        if (reserva.getFechaHora().isBefore(now)) {
            throw new IllegalArgumentException("No se pueden crear reservas para fechas u horas pasadas.");
        }

        // --- VALIDACIÓN FINAL DE DISPONIBILIDAD DEL SLOT (Anti-doble reserva) ---
        // Se hace para el POOL de canchas de un TIPO dentro de un COMPLEJO.
        LocalDateTime slotEndTime = reserva.getFechaHora().plusMinutes(SLOT_DURATION_MINUTES);

        // Contar cuántas canchas de este tipo ya están reservadas para este slot en este complejo
        List<Reserva> conflictosExistentes = reservaRepositorio.findConflictingReservationsForPool(
                complejoAsignado.getId(),
                reserva.getTipoCanchaReservada(),
                reserva.getFechaHora(),
                slotEndTime
        );

        // Obtener la cantidad total de canchas de este tipo en el complejo
        Integer totalCanchasDeEsteTipo = complejoAsignado.getCanchaCounts().getOrDefault(reserva.getTipoCanchaReservada(), 0);

        if (conflictosExistentes.size() >= totalCanchasDeEsteTipo) {
            log.warn("Conflicto: Todas las {} canchas de tipo '{}' en complejo '{}' ya están reservadas para el slot {}.",
                    totalCanchasDeEsteTipo, reserva.getTipoCanchaReservada(), complejoAsignado.getNombre(), reserva.getFechaHora());
            throw new IllegalArgumentException("No hay canchas disponibles para el tipo y horario seleccionado en este complejo. Por favor, elige otro.");
        }

        // --- ASIGNACIÓN INTERNA DE LA "INSTANCIA" DE CANCHA ---
        // Aquí puedes generar un identificador para la instancia de cancha asignada (ej. "Fútbol 5 - Instancia 3")
        // Simplemente lo creamos como un String para almacenar.
        String nombreCanchaAsignada = reserva.getTipoCanchaReservada() + " - Instancia " + (conflictosExistentes.size() + 1);
        reserva.setNombreCanchaAsignada(nombreCanchaAsignada);

        // El precio ya debe venir del Complejo en el controlador o DTO
        if (reserva.getPrecio() == null || reserva.getPrecio().compareTo(BigDecimal.ZERO) < 0) {
            // Este precio debería obtenerse del complejo. Se asume que el controlador ya lo ha asignado al DTO.
            log.error("El precio de la reserva es nulo o inválido al intentar guardar.");
            throw new IllegalStateException("Error interno: El precio de la reserva no está definido.");
        }

        // --- ASIGNACIÓN DE ESTADO INICIAL ---
        if ("efectivo".equalsIgnoreCase(reserva.getMetodoPago())) {
            reserva.setPagada(false);
            reserva.setEstado("pendiente_pago_efectivo");
        } else { // Para Mercado Pago
            reserva.setPagada(false);
            reserva.setEstado("pendiente_pago_mp");
        }
        reserva.setFechaPago(null);
        reserva.setMercadoPagoPaymentId(null);

        Reserva reservaGuardada = reservaRepositorio.save(reserva);
        log.info("Reserva creada con ID: {} para complejo {} (tipo: {}) y asignada a: {}",
                reservaGuardada.getId(), complejoAsignado.getNombre(), reserva.getTipoCanchaReservada(), nombreCanchaAsignada);

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

        // Unificar el estado 'confirmada' con 'pagada' o 'pendiente_pago_efectivo'
        if ("pendiente_pago_mp".equalsIgnoreCase(r.getEstado())) {
            r.setEstado("confirmada"); // Si el admin la confirma antes que MP pague
        } else if ("pendiente".equalsIgnoreCase(r.getEstado())) { // Si estaba en estado genérico 'pendiente'
            r.setEstado("confirmada");
        }
        // Si ya está pagada o pendiente_pago_efectivo, no hay más que confirmar.

        Reserva reservaConfirmada = reservaRepositorio.save(r);
        log.info("Reserva con ID: {} confirmada exitosamente. Nuevo estado: {}", id, reservaConfirmada.getEstado());
        return reservaConfirmada;
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
        log.info("Buscando reservas (con usuario y complejo) para Usuario ID: {}", usuario.getId());
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

    // --- NUEVO MÉTODO: Contar canchas disponibles por tipo para un slot dado en un complejo ---
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

        // Contar cuántas reservas existen que bloquean el slot para este tipo de cancha en este complejo
        List<Reserva> conflictos = reservaRepositorio.findConflictingReservationsForPool(
                complejoId,
                tipoCancha,
                slotStartTime,
                slotEndTime
        );

        int bookedCount = conflictos.size();
        int availableCount = totalCanchasDeEsteTipo - bookedCount;

        // Asegurarse de no devolver un número negativo
        availableCount = Math.max(0, availableCount);

        // Considerar la hora actual si la fecha es hoy
        if (fecha.isEqual(LocalDate.now())) {
            LocalTime nowTime = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
            if (hora.isBefore(nowTime) || hora.equals(nowTime)) { // Si el slot ya pasó o está pasando
                log.debug("Slot {} para tipo {} en complejo {} está en el pasado o presente, marcando como 0 disponibles.", hora, tipoCancha, complejoId);
                availableCount = 0;
            }
        }

        log.debug("Encontradas {} canchas de tipo '{}' disponibles en complejo '{}' para {} a las {}. (Total: {}, Reservadas: {})",
                availableCount, tipoCancha, complejo.getNombre(), fecha, hora, totalCanchasDeEsteTipo, bookedCount);
        return availableCount;
    }

    // NUEVO MÉTODO: Obtener la "instancia" de cancha asignada (ej. "Fútbol 5 - Instancia 3")
    // Este método es usado INTERNAMENTE por el controlador/servicio para asignar un nombre a la reserva.
    @Transactional(readOnly = true)
    public Optional<String> generateAssignedCanchaName(Long complejoId, String tipoCancha, LocalDate fecha, LocalTime hora) {
        log.info("Generando nombre de cancha asignada para complejo ID: {} tipo: {} en slot: {}", complejoId, tipoCancha, fecha + " " + hora);

        Complejo complejo = complejoRepositorio.findById(complejoId)
                .orElseThrow(() -> new IllegalArgumentException("Complejo no encontrado con ID: " + complejoId));

        Integer totalCanchasDeEsteTipo = complejo.getCanchaCounts().getOrDefault(tipoCancha, 0);
        if (totalCanchasDeEsteTipo <= 0) {
            return Optional.empty(); // No hay canchas de este tipo
        }

        LocalDateTime slotStartTime = LocalDateTime.of(fecha, hora);
        LocalDateTime slotEndTime = slotStartTime.plusMinutes(SLOT_DURATION_MINUTES);

        List<Reserva> conflictos = reservaRepositorio.findConflictingReservationsForPool(
                complejoId,
                tipoCancha,
                slotStartTime,
                slotEndTime
        );

        // La "instancia" asignada será el número de reservas existentes + 1
        // Esto asume que las instancias se nombran de 1 a N.
        if (conflictos.size() < totalCanchasDeEsteTipo) {
            String nombreInstancia = tipoCancha + " - Instancia " + (conflictos.size() + 1);
            log.debug("Nombre de instancia asignado: {}", nombreInstancia);
            return Optional.of(nombreInstancia);
        }

        log.debug("Todas las canchas de tipo '{}' en complejo '{}' están ocupadas para el slot {}.", tipoCancha, complejo.getNombre(), fecha + " " + hora);
        return Optional.empty(); // No hay instancias disponibles
    }


    // El método `getAvailableTimeSlots(Long canchaId, LocalDate fecha)` antiguo ya no es útil
    // para el flujo de "pool de canchas" y debería ser eliminado si ya no hay canchas individuales.
    // Si aún se usa en alguna parte (ej. Admin Panel para ver un calendario de UNA cancha física),
    // debería adaptarse para trabajar con la nueva estructura o eliminarse.
    // Lo comento aquí para que no genere un error de compilación.
    /*
    @Transactional(readOnly = true)
    public List<String> getAvailableTimeSlots(Long canchaId, LocalDate fecha) {
        log.warn("Llamada a método getAvailableTimeSlots(canchaId, fecha) que está siendo deprecado/revisado.");
        // Esta lógica necesitaría reevaluarse si ya no tienes entidades 'Cancha'.
        // Podrías mapearlo a una vista de un solo tipo de cancha o eliminarlo.
        return Collections.emptyList();
    }
    */

    // --- Método calcularEstadisticas (Reincorporado y corregido) ---
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
                .filter(reserva -> "pagada".equalsIgnoreCase(reserva.getEstado()) || "confirmada".equalsIgnoreCase(reserva.getEstado()) || "pendiente_pago_efectivo".equalsIgnoreCase(reserva.getEstado()))
                .count();

        Long totalReservasPendientes = todasLasReservas.stream()
                .filter(reserva -> "pendiente".equalsIgnoreCase(reserva.getEstado()) || "pendiente_pago_mp".equalsIgnoreCase(reserva.getEstado()))
                .count();

        Long totalReservasCanceladas = todasLasReservas.stream()
                .filter(reserva -> "cancelada".equalsIgnoreCase(reserva.getEstado()) || "rechazada_pago_mp".equalsIgnoreCase(reserva.getEstado()))
                .count();
        log.debug("Reservas: Confirmadas={}, Pendientes={}, Canceladas={}",
                totalReservasConfirmadas, totalReservasPendientes, totalReservasCanceladas);

        // MODIFICADO: Las estadísticas por cancha ahora son por TIPO DE CANCHA RESERVADA
        Map<String, Long> reservasPorTipoCancha = todasLasReservas.stream()
                .collect(Collectors.groupingBy(Reserva::getTipoCanchaReservada, Collectors.counting()));
        log.debug("Reservas por tipo de cancha: {}", reservasPorTipoCancha);

        Map<String, Long> horariosPico = todasLasReservas.stream()
                .map(reserva -> reserva.getFechaHora().toLocalTime().truncatedTo(ChronoUnit.HOURS))
                .collect(Collectors.groupingBy(LocalTime::toString, Collectors.counting()));
        log.debug("Horarios pico: {}", horariosPico);


        return new EstadisticasResponse(
                ingresosTotalesConfirmados,
                totalReservasConfirmadas,
                totalReservasPendientes,
                totalReservasCanceladas,
                reservasPorTipoCancha, // Usar el mapa por tipo de cancha
                horariosPico
        );
    }
}