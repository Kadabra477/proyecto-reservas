package com.example.reservafutbol.Repositorio;

import com.example.reservafutbol.Modelo.Complejo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComplejoRepositorio extends JpaRepository<Complejo, Long> {
    // Puedes añadir métodos de búsqueda específicos para Complejo si los necesitas
    Optional<Complejo> findByNombre(String nombre);
    List<Complejo> findByUbicacion(String ubicacion);
}