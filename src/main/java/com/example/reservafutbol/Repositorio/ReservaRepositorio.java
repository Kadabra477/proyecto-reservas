package com.example.reservafutbol.Repositorio;

import com.example.reservafutbol.Modelo.Reserva;
import com.example.reservafutbol.Modelo.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservaRepositorio extends JpaRepository<Reserva, Long> {

    List<Reserva> findByCanchaId(Long canchaId);

    @EntityGraph(attributePaths = {"usuario", "cancha", "jugadores", "equipo1", "equipo2"})
    List<Reserva> findByUsuario(User usuario);

    Reserva findByPreferenceId(String preferenceId);

    @EntityGraph(attributePaths = {"usuario", "cancha", "jugadores", "equipo1", "equipo2"})
    List<Reserva> findAll();

    @Override
    @EntityGraph(attributePaths = {"usuario", "cancha", "jugadores", "equipo1", "equipo2"})
    Optional<Reserva> findById(Long id);

    // CONSULTA CORREGIDA FINAL: Encontrar reservas que se solapan con un nuevo slot
    // Cambiado 'MINUTE' (con comillas) a MINUTE (sin comillas) para el TIMESTAMPADD
    @Query("SELECT r FROM Reserva r WHERE r.cancha.id = :canchaId AND " +
            "(r.estado = 'confirmada' OR r.estado = 'pagada' OR r.estado = 'confirmada_efectivo' OR r.estado = 'pendiente_pago_mp') AND " +
            "(r.fechaHora < :endTime AND FUNCTION('TIMESTAMPADD', MINUTE, 60, r.fechaHora) > :startTime)")
    List<Reserva> findConflictingReservations(
            @Param("canchaId") Long canchaId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    // Esta consulta ya estaba correcta
    @Query("SELECT r FROM Reserva r WHERE r.cancha.id = :canchaId AND " +
            "FUNCTION('DATE', r.fechaHora) = :fecha AND " +
            "(r.estado = 'confirmada' OR r.estado = 'pagada' OR r.estado = 'confirmada_efectivo' OR r.estado = 'pendiente_pago_mp')")
    List<Reserva> findOccupiedSlotsByCanchaAndDate(
            @Param("canchaId") Long canchaId,
            @Param("fecha") LocalDate fecha
    );
}