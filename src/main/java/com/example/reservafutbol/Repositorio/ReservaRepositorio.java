package com.example.reservafutbol.Repositorio;

import com.example.reservafutbol.Modelo.Reserva;
import com.example.reservafutbol.Modelo.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional; // Importar Optional

@Repository
public interface ReservaRepositorio extends JpaRepository<Reserva, Long> {

    List<Reserva> findByCanchaId(Long canchaId);

    @EntityGraph(attributePaths = {"usuario", "cancha", "jugadores", "equipo1", "equipo2"})
    List<Reserva> findByUsuario(User usuario);

    Reserva findByPreferenceId(String preferenceId);

    @EntityGraph(attributePaths = {"usuario", "cancha", "jugadores", "equipo1", "equipo2"})
    List<Reserva> findAll();

    // OPCIONAL: Añadir EntityGraph al findById si lo usas en otros lugares y necesitas el usuario/cancha.
    // Esto asegura que la Reserva traída por ID también tenga el User y Cancha cargados.
    @Override
    @EntityGraph(attributePaths = {"usuario", "cancha", "jugadores", "equipo1", "equipo2"})
    Optional<Reserva> findById(Long id);
}