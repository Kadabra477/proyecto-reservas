package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Modelo.Reserva;
import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Repositorio.ReservaRepositorio;
import com.example.reservafutbol.Repositorio.UsuarioRepositorio; // Importar Repo de Usuario
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException; // Para buscar usuario
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Usar transacciones

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class ReservaServicio {

    private static final Logger log = LoggerFactory.getLogger(ReservaServicio.class);

    @Autowired
    private ReservaRepositorio reservaRepositorio;

    @Autowired
    private UsuarioRepositorio usuarioRepositorio; // Inyectar repo de usuario

    public List<Reserva> listarReservas(Long canchaId) {
        log.info("Buscando reservas para cancha ID: {}", canchaId);
        return reservaRepositorio.findByCanchaId(canchaId);
    }

    // --- CREAR RESERVA (Asume que el objeto viene con User y Precio seteados desde el controller) ---
    @Transactional // Buena práctica para operaciones de escritura
    public Reserva crearReserva(Reserva reserva) {
        log.info("Intentando crear reserva para cancha ID: {} por usuario: {} en fecha/hora: {}",
                reserva.getCancha() != null ? reserva.getCancha().getId() : "null",
                reserva.getUsuario() != null ? reserva.getUsuario().getUsername() : reserva.getUserEmail(), // Loguear usuario
                reserva.getFechaHora());

        // Validaciones básicas
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

        // Setear valores iniciales por defecto (aunque la entidad ya los tenga)
        reserva.setConfirmada(false); // Las reservas inician sin confirmar
        reserva.setPagada(false);
        reserva.setEstado("pendiente"); // El estado se actualizará con @PrePersist/Update
        reserva.setMetodoPago(null);
        reserva.setFechaPago(null);
        reserva.setMercadoPagoPaymentId(null);


        // Aquí podrías añadir lógica para verificar disponibilidad horaria si es necesario

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
        Reserva r = reservaRepositorio.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada con ID: " + id));

        if (Boolean.TRUE.equals(r.getConfirmada())) {
            log.warn("Reserva con ID: {} ya estaba confirmada.", id);
            return r;
        } else {
            r.setConfirmada(true);
            // El estado se actualizará automáticamente por @PreUpdate en la entidad
            // r.setEstado("confirmada"); // Ya no es necesario aquí
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

    public List<Reserva> listarTodas() {
        return reservaRepositorio.findAll();
    }

    // --- OBTENER RESERVAS POR USERNAME (Usa el objeto User) ---
    public List<Reserva> obtenerReservasPorUsername(String username) {
        log.debug("Buscando usuario con username/email: {}", username);
        // Busca el usuario por su email (que es el username en tu caso)
        User usuario = usuarioRepositorio.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Usuario no encontrado para obtener reservas: {}", username);
                    // Lanzar excepción específica o devolver lista vacía
                    return new UsernameNotFoundException("Usuario no encontrado: " + username);
                });
        log.info("Buscando reservas para Usuario ID: {}", usuario.getId());
        // Llama al método del repositorio que busca por objeto User
        return reservaRepositorio.findByUsuario(usuario);
    }

    // --- MARCAR COMO PAGADA ---
    public Reserva marcarComoPagada(Long id, String metodoPago, String mercadoPagoPaymentId) {
        Reserva reserva = reservaRepositorio.findById(id).orElseThrow(() -> new RuntimeException("Reserva no encontrada"));
        reserva.setEstado("Pagada");
        reserva.setMetodoPago(metodoPago);
        reserva.setMercadoPagoPaymentId(mercadoPagoPaymentId); // este campo debe existir en tu entidad
        return reservaRepositorio.save(reserva);
    }


    // --- Generar Equipos (Sin cambios relevantes) ---
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
        List<String> equipo1 = new ArrayList<>();
        List<String> equipo2 = new ArrayList<>();
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