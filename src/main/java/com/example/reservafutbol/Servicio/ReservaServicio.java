package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Modelo.Reserva;
import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Repositorio.ReservaRepositorio;
import com.example.reservafutbol.Repositorio.UsuarioRepositorio;
import com.example.reservafutbol.payload.response.EstadisticasResponse; // Importa el nuevo DTO
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;   // Necesario para extraer la hora
import java.util.ArrayList;
import java.util.Collections;
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
        // Asignar el nombre de la cancha al campo directo de la reserva
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

        reserva.setConfirmada(false);
        reserva.setPagada(false);
        reserva.setEstado("pendiente"); // Estado inicial antes de la lógica @PrePersist
        reserva.setMetodoPago(reserva.getMetodoPago());
        reserva.setFechaPago(null);
        reserva.setMercadoPagoPaymentId(null);

        // La lógica @PrePersist/@PreUpdate en la entidad Reserva se encarga de ajustar 'estado'

        Reserva reservaGuardada = reservaRepositorio.save(reserva);
        log.info("Reserva creada con ID: {}", reservaGuardada.getId());
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

        if (Boolean.TRUE.equals(r.getConfirmada())) {
            log.warn("Reserva con ID: {} ya estaba confirmada.", id);
            return r;
        } else {
            r.setConfirmada(true);
            // La lógica @PreUpdate en la entidad Reserva se encarga de ajustar 'estado'
            Reserva reservaGuardada = reservaRepositorio.save(r);
            log.info("Reserva con ID: {} confirmada exitosamente.", id);
            return reservaGuardada;
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
        // La lógica @PreUpdate en la entidad Reserva se encarga de ajustar 'estado'
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

    // --- NUEVO MÉTODO PARA ESTADÍSTICAS ---
    @Transactional(readOnly = true) // Es una operación de solo lectura
    public EstadisticasResponse calcularEstadisticas() {
        log.info("Calculando estadísticas del complejo...");
        List<Reserva> todasLasReservas = reservaRepositorio.findAll();

        // 1. Ingresos Totales Confirmados y Pagados
        BigDecimal ingresosTotalesConfirmados = todasLasReservas.stream()
                .filter(Reserva::getConfirmada) // Filtra las que el admin confirmó
                .filter(Reserva::getPagada)     // Filtra las que están marcadas como pagadas
                .map(Reserva::getPrecio) // Asegúrate de usar getPrecio() y no getPrecioTotal si ese es el nombre del campo
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        log.debug("Ingresos totales confirmados y pagados: {}", ingresosTotalesConfirmados);


        // 2. Total de Reservas por Estado (Confirmadas, Pendientes, Canceladas)
        Long totalReservasConfirmadas = todasLasReservas.stream()
                .filter(Reserva::getConfirmada)
                .count();

        // Las "pendientes" son las que no están confirmadas por el admin
        Long totalReservasPendientes = todasLasReservas.stream()
                .filter(reserva -> !reserva.getConfirmada())
                .count();

        // Asumimos que no hay un campo explícito 'cancelada', sino un estado.
        // Si tu modelo lo soporta, podrías filtrar por estado="cancelada".
        Long totalReservasCanceladas = todasLasReservas.stream()
                .filter(reserva -> "cancelada".equalsIgnoreCase(reserva.getEstado())) // Asumiendo que "cancelada" es un valor del campo 'estado'
                .count();
        log.debug("Reservas: Confirmadas={}, Pendientes={}, Canceladas={}",
                totalReservasConfirmadas, totalReservasPendientes, totalReservasCanceladas);


        // 3. Reservas por Cancha (Nombre de la cancha -> Cantidad)
        Map<String, Long> reservasPorCancha = todasLasReservas.stream()
                .collect(Collectors.groupingBy(Reserva::getCanchaNombre, Collectors.counting()));
        log.debug("Reservas por cancha: {}", reservasPorCancha);

        // 4. Horarios Pico (Hora de inicio -> Cantidad de reservas)
        Map<String, Long> horariosPico = todasLasReservas.stream()
                .map(reserva -> reserva.getFechaHora().toLocalTime()) // Obtiene LocalTime de fechaHora
                // Agrupamos por la hora completa (ej. "15:00:00")
                .collect(Collectors.groupingBy(LocalTime::toString, Collectors.counting()));
        log.debug("Horarios pico: {}", horariosPico);


        return new EstadisticasResponse(
                ingresosTotalesConfirmados,
                totalReservasConfirmadas,
                totalReservasPendientes,
                totalReservasCanceladas, // Asegúrate de que esta cuenta sea correcta si manejas 'cancelada'
                reservasPorCancha,
                horariosPico
        );
    }
}