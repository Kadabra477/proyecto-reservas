package com.example.reservafutbol.Repositorio;

import com.example.reservafutbol.Modelo.Complejo;
import com.example.reservafutbol.Modelo.User; // Importar User
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComplejoRepositorio extends JpaRepository<Complejo, Long> {
    Optional<Complejo> findByNombre(String nombre);
    List<Complejo> findByUbicacion(String ubicacion);

    // Nuevo método: Buscar complejos por propietario
    List<Complejo> findByPropietario(User propietario);
}