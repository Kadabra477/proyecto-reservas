package com.example.reservafutbol.Repositorio;

import com.example.reservafutbol.Modelo.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UsuarioRepositorio extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByValidationToken(String token);
    Optional<User> findByPasswordResetToken(String token);
}