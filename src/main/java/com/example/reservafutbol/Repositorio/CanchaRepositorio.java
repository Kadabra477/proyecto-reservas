package com.example.reservafutbol.Repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CanchaRepositorio extends JpaRepository<Cancha, Long> {
    // NUEVO MÉTODO: Listar canchas por tipo y que estén activas (disponibles en general)
    List<Cancha> findByTipoCanchaAndDisponibleTrue(String tipoCancha);

    // Si tu AdminPanel usa `findByNombre`, mantenlo. Si no, puedes quitarlo.
    // Optional<Cancha> findByNombre(String nombre);
}