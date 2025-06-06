package com.example.reservafutbol.Controlador;

import com.example.reservafutbol.Servicio.ReservaServicio;
import com.example.reservafutbol.payload.response.EstadisticasResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/estadisticas")
public class EstadisticasControlador {

    private static final Logger log = LoggerFactory.getLogger(EstadisticasControlador.class);

    @Autowired
    private ReservaServicio reservaServicio;

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EstadisticasResponse> getEstadisticasAdmin() {
        log.info("GET /api/estadisticas/admin - Solicitud de estadísticas de administrador.");
        EstadisticasResponse estadisticas = reservaServicio.calcularEstadisticas(); // Llama al método del servicio
        return ResponseEntity.ok(estadisticas);
    }
}