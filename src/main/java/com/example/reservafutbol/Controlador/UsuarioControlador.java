package com.example.reservafutbol.Controlador;

import com.example.reservafutbol.DTO.PerfilDTO;
import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Repositorio.UsuarioRepositorio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/usuario")
public class UsuarioControlador {

    @Autowired
    private UsuarioRepositorio usuarioRepositorio;

    @PatchMapping("/perfil")
    public ResponseEntity<?> actualizarPerfil(@RequestBody PerfilDTO perfil, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body("Debe iniciar sesión.");
        }

        String email = auth.getName();
        Optional<User> optUser = usuarioRepositorio.findByUsername(email);
        if (optUser.isEmpty()) {
            return ResponseEntity.status(404).body("Usuario no encontrado.");
        }

        User u = optUser.get();
        u.setNombreCompleto(perfil.getNombreCompleto());
        u.setUbicacion(perfil.getUbicacion());
        u.setEdad(perfil.getEdad());
        u.setCompletoPerfil(true);

        usuarioRepositorio.save(u);
        return ResponseEntity.ok("Perfil actualizado correctamente.");
    }
}
