package com.example.reservafutbol.Repositorio;

import com.example.reservafutbol.Modelo.Cancha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional; // Asegúrate de importar Optional

@Repository
public interface CanchaRepositorio extends JpaRepository<Cancha, Long> {
    // NUEVO MÉTODO: Listar canchas por tipo y que estén activas (disponibles en general)
    List<Cancha> findByTipoCanchaAndDisponibleTrue(String tipoCancha);

    // Si tu AdminPanel usa `findByNombre`, mantenlo. Si no, puedes quitarlo.
    // Optional<Cancha> findByNombre(String nombre);
}