package com.example.reservafutbol.Repositorio;

import com.example.reservafutbol.Modelo.Reserva;
import com.example.reservafutbol.Modelo.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReservaRepositorio extends JpaRepository<Reserva, Long> {

    List<Reserva> findByCanchaId(Long canchaId);

    // Ya tenías este, que está bien para obtener las reservas de un usuario específico
    @EntityGraph(attributePaths = {"usuario", "cancha", "jugadores", "equipo1", "equipo2"})
    List<Reserva> findByUsuario(User usuario);

    Reserva findByPreferenceId(String preferenceId);

    // NUEVO: Método para buscar todas las reservas cargando el usuario y la cancha eagerly
    @EntityGraph(attributePaths = {"usuario", "cancha", "jugadores", "equipo1", "equipo2"})
    List<Reserva> findAll(); // Sobrescribe el findAll predeterminado para cargar las relaciones
}