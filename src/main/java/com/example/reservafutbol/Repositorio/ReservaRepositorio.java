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

    // ELIMINADO: Ya no se buscan reservas por canchaId específica
    // List<Reserva> findByCanchaId(Long canchaId);

    // NUEVO MÉTODO: Buscar reservas que se solapan para un COMPLEJO y TIPO DE CANCHA específico
    // Usado para verificar conflictos en el pool de canchas.
    @Query("SELECT r FROM Reserva r WHERE r.complejo.id = :complejoId AND r.tipoCanchaReservada = :tipoCancha AND " +
            "(r.estado = 'pagada' OR r.estado = 'pendiente_pago_efectivo' OR r.estado = 'pendiente_pago_mp') AND " + // Estados que bloquean el slot
            "(r.fechaHora < :endTime AND FUNCTION('TIMESTAMPADD', 'MINUTE', 60, r.fechaHora) > :startTime)")
    List<Reserva> findConflictingReservationsForPool(
            @Param("complejoId") Long complejoId,
            @Param("tipoCancha") String tipoCancha,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    // NUEVO MÉTODO: Buscar todas las reservas de un COMPLEJO y TIPO DE CANCHA en una fecha específica
    // Útil para el administrador para ver el "calendario" de un tipo de cancha.
    @Query("SELECT r FROM Reserva r WHERE r.complejo.id = :complejoId AND r.tipoCanchaReservada = :tipoCancha AND " +
            "FUNCTION('DATE', r.fechaHora) = :fecha AND " +
            "(r.estado = 'pagada' OR r.estado = 'pendiente_pago_efectivo' OR r.estado = 'pendiente_pago_mp')")
    List<Reserva> findOccupiedSlotsByComplejoAndTipoCanchaAndDate(
            @Param("complejoId") Long complejoId,
            @Param("tipoCancha") String tipoCancha,
            @Param("fecha") LocalDate fecha
    );


    @EntityGraph(attributePaths = {"usuario", "complejo"}) // Adaptar EntityGraph para Complejo
    List<Reserva> findByUsuario(User usuario);

    Reserva findByPreferenceId(String preferenceId);

    @EntityGraph(attributePaths = {"usuario", "complejo"}) // Adaptar EntityGraph para Complejo
    List<Reserva> findAll();

    @Override
    @EntityGraph(attributePaths = {"usuario", "complejo"}) // Adaptar EntityGraph para Complejo
    Optional<Reserva> findById(Long id);
}