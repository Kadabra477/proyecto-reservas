package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Modelo.Cancha;
import com.example.reservafutbol.Repositorio.CanchaRepositorio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CanchaServicio {

    @Autowired
    private CanchaRepositorio canchaRepositorio;

    // Obtener todas las canchas
    public List<Cancha> listarCanchas() {
        return canchaRepositorio.findAll();
    }

    // Crear una nueva cancha
    public Cancha crearCancha(Cancha cancha) {
        if (cancha.getNombre() == null || cancha.getNombre().isEmpty()) {
            throw new IllegalArgumentException("El nombre de la cancha no puede estar vacío");
        }
        return canchaRepositorio.save(cancha);
    }

    public Optional<Cancha> buscarPorId(Long id) {
        return canchaRepositorio.findById(id);
    }

    // Actualizar una cancha existente
    public Cancha actualizarCancha(Long id, Cancha cancha) {
        if (!canchaRepositorio.existsById(id)) {
            return null;
        }
        if (cancha.getNombre() == null || cancha.getNombre().isEmpty()) {
            throw new IllegalArgumentException("El nombre de la cancha no puede estar vacío");
        }
        cancha.setId(id);
        return canchaRepositorio.save(cancha);
    }

    public boolean eliminarCancha(Long id) {
        if (canchaRepositorio.existsById(id)) {
            canchaRepositorio.deleteById(id);
            return true;
        } else {
            throw new IllegalArgumentException("La cancha no existe");
        }
    }
}
