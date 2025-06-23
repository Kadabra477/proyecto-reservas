package com.example.reservafutbol.Repositorio;

import com.example.reservafutbol.Modelo.Complejo;
import com.example.reservafutbol.Modelo.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query; // Importar Query
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComplejoRepositorio extends JpaRepository<Complejo, Long> {

    Optional<Complejo> findByNombre(String nombre);
    List<Complejo> findByPropietario(User propietario);

    // Nuevo método para cargar complejos incluyendo el propietario (JOIN FETCH)
    // Esto evita LazyInitializationException cuando se accede al propietario.
    @Query("SELECT c FROM Complejo c JOIN FETCH c.propietario")
    List<Complejo> findAllWithPropietario();

    // Nuevo método para buscar por ID incluyendo el propietario
    @Query("SELECT c FROM Complejo c JOIN FETCH c.propietario WHERE c.id = :id")
    Optional<Complejo> findByIdWithPropietario(Long id);

    // Nuevo método para buscar complejos por propietario incluyendo el propietario (útil si hay lazy loading)
    @Query("SELECT c FROM Complejo c JOIN FETCH c.propietario WHERE c.propietario = :propietario")
    List<Complejo> findByPropietarioWithPropietario(User propietario);
}