package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Modelo.Reserva;
import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Repositorio.ReservaRepositorio;
import com.example.reservafutbol.Repositorio.UsuarioRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
        log.info("Intentando crear reserva para cancha ID: {} por usuario: {} en fecha/hora: {}",
                reserva.getCancha() != null ? reserva.getCancha().getId() : "null",
                reserva.getUsuario() != null ? reserva.getUsuario().getUsername() : reserva.getUserEmail(),
                reserva.getFechaHora());

        if (reserva.getUsuario() == null && (reserva.getUserEmail() == null || reserva.getUserEmail().isBlank())) {
            throw new IllegalArgumentException("La reserva debe estar asociada a un usuario (email).");
        }
        if (reserva.getCancha() == null) {
            throw new IllegalArgumentException("La cancha es obligatoria.");
        }
        if (reserva.getFechaHora() == null) {
            throw new IllegalArgumentException("La fecha y hora son obligatorias.");
        }
        if (reserva.getPrecio() == null || reserva.getPrecio().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El precio de la reserva debe ser válido.");
        }

        reserva.setConfirmada(false);
        reserva.setPagada(false);
        reserva.setEstado("pendiente");
        reserva.setMetodoPago(null);
        reserva.setFechaPago(null);
        reserva.setMercadoPagoPaymentId(null);

        Reserva reservaGuardada = reservaRepositorio.save(reserva);
        log.info("Reserva creada con ID: {}", reservaGuardada.getId());
        return reservaGuardada;
    }

    public Optional<Reserva> obtenerReserva(Long id) {
        log.info("Buscando reserva con ID: {}", id);
        return reservaRepositorio.findById(id);
    }

    @Transactional
    public Reserva confirmarReserva(Long id) {
        log.info("Intentando confirmar reserva con ID: {}", id);
        // Usar findById con fetch de User/Cancha si es necesario aquí,
        // o depender de que el DTO lo cargue al crearse.
        // Si el EntityGraph en el findById está configurado, no hay problema.
        Reserva r = reservaRepositorio.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada con ID: " + id));

        // Para asegurar que el User está cargado antes de que el DTO lo acceda si fuera necesario
        // Aunque el DTO ya lo tiene como lazy en la entidad, al construir el DTO se accederá.
        // Una forma de forzar la carga:
        // Hibernate.initialize(r.getUsuario());
        // Hibernate.initialize(r.getCancha());

        if (Boolean.TRUE.equals(r.getConfirmada())) {
            log.warn("Reserva con ID: {} ya estaba confirmada.", id);
            return r;
        } else {
            r.setConfirmada(true);
            Reserva reservaGuardada = reservaRepositorio.save(r);
            log.info("Reserva con ID: {} confirmada exitosamente.", id);
            // Retorna la reserva guardada. El controlador la mapeará al DTO.
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

    // MODIFICADO: Añadimos @Transactional(readOnly = true) para asegurar que la sesión esté abierta
    // cuando se accede a las relaciones cargadas por @EntityGraph.
    // El método findAll() ahora usa el @EntityGraph en el repositorio.
    @Transactional(readOnly = true)
    public List<Reserva> listarTodas() {
        return reservaRepositorio.findAll(); // Llama al findAll que usa el EntityGraph
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
        // Aquí también podríamos forzar la carga de usuario si no está en el DTO de confirmación.
        // Hibernate.initialize(reserva.getUsuario());
        reserva.setEstado("Pagada");
        reserva.setMetodoPago(metodoPago);
        reserva.setMercadoPagoPaymentId(mercadoPagoPaymentId);
        return reservaRepositorio.save(reserva);
    }

    @Transactional
    public Reserva generarEquipos(Long id) {
        log.info("Generando equipos para reserva ID: {}", id);
        Reserva r = reservaRepositorio.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada con ID: " + id));
        // Aquí también podríamos forzar la carga de usuario si no está en el DTO de confirmación.
        // Hibernate.initialize(r.getUsuario());
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
}