package com.example.reservafutbol.Repositorio;

import com.example.reservafutbol.Modelo.Complejo;
import com.example.reservafutbol.Modelo.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query; // ¡Importa Query!
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComplejoRepositorio extends JpaRepository<Complejo, Long> {

    Optional<Complejo> findByNombre(String nombre);
    List<Complejo> findByPropietario(User propietario);

    // Método para cargar todos los complejos incluyendo el propietario
    @Query("SELECT c FROM Complejo c JOIN FETCH c.propietario")
    List<Complejo> findAllWithPropietario();

    // Método para buscar un complejo por ID incluyendo el propietario
    @Query("SELECT c FROM Complejo c JOIN FETCH c.propietario WHERE c.id = :id")
    Optional<Complejo> findByIdWithPropietario(Long id);

    // Método para buscar complejos por un propietario específico incluyendo el propietario
    @Query("SELECT c FROM Complejo c JOIN FETCH c.propietario WHERE c.propietario = :propietario")
    List<Complejo> findByPropietarioWithPropietario(User propietario);
}