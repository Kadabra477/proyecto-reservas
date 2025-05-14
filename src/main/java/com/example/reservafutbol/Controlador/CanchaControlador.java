package com.example.reservafutbol.Controlador;

import com.example.reservafutbol.Modelo.Cancha;
import com.example.reservafutbol.Servicio.CanchaServicio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/canchas") // 👈 ¡Añade esta anotación!
public class CanchaControlador {

    @Autowired
    private CanchaServicio canchaServicio;

    @GetMapping("/{id}") // <--- ¡Esta es la anotación clave!
    public ResponseEntity<Cancha> obtenerCanchaPorId(@PathVariable Long id) {
        // Asume que tienes un método en tu canchaServicio para buscar por ID
        Optional<Cancha> canchaOptional = canchaServicio.buscarPorId(id); // O como se llame tu método en el servicio

        if (canchaOptional.isPresent()) {
            // Si se encuentra la cancha, devuélvela con un código 200 OK
            return ResponseEntity.ok(canchaOptional.get());
        } else {
            // Si no se encuentra, devuelve un código 404 Not Found
            return ResponseEntity.notFound().build();
        }
    }

    // Obtener todas las canchas (solo permitidos a usuarios autenticados)
    @GetMapping
    public List<Cancha> obtenerCanchas() {
        return canchaServicio.listarCanchas();
    }

    // Crear una nueva cancha (requiere autenticación)
    @PreAuthorize("hasRole('ADMIN')")  // Solo admin puede crear una cancha
    @PostMapping
    public ResponseEntity<Cancha> crearCancha(@RequestBody Cancha cancha) {
        Cancha nuevaCancha = canchaServicio.crearCancha(cancha);
        return new ResponseEntity<>(nuevaCancha, HttpStatus.CREATED); // Retorna un código 201
    }

    // Actualizar una cancha existente (requiere autenticación)
    @PreAuthorize("hasRole('ADMIN')")  // Solo admin puede actualizar una cancha
    @PutMapping("/{id}")
    public ResponseEntity<Cancha> actualizarCancha(@PathVariable Long id, @RequestBody Cancha cancha) {
        Cancha canchaActualizada = canchaServicio.actualizarCancha(id, cancha);
        if (canchaActualizada != null) {
            return new ResponseEntity<>(canchaActualizada, HttpStatus.OK); // Retorna el código 200
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND); // Si no se encuentra la cancha, retorna 404
    }

    // Eliminar una cancha (requiere autenticación)
    @PreAuthorize("hasRole('ADMIN')")  // Solo admin puede eliminar una cancha
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarCancha(@PathVariable Long id) {
        boolean eliminado = canchaServicio.eliminarCancha(id);
        if (eliminado) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT); // Retorna el código 204
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND); // Si no se encuentra la cancha, retorna 404
    }
}