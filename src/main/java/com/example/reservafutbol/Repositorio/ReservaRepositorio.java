package com.example.reservafutbol.Repositorio;

import com.example.reservafutbol.Modelo.Reserva;
import com.example.reservafutbol.Modelo.User;
import org.springframework.data.jpa.repository.EntityGraph; // Importar EntityGraph
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReservaRepositorio extends JpaRepository<Reserva, Long> {

    List<Reserva> findByCanchaId(Long canchaId);
    @EntityGraph(attributePaths = {"usuario", "cancha"})
    List<Reserva> findByUsuario(User usuario);

    Reserva findByPreferenceId(String preferenceId);
}