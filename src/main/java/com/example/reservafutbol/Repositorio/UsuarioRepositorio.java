package com.example.reservafutbol.Repositorio;

import com.example.reservafutbol.Modelo.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepositorio extends JpaRepository<User, Long> {
    // findByUsername ahora buscará por el correo electrónico (que es el username)
    Optional<User> findByUsername(String username);
    // existsByUsername ahora verificará si el correo electrónico (username) ya existe
    Boolean existsByUsername(String username);

    // ELIMINADOS: findByEmail y existsByEmail ya no son necesarios si username es el email.
    // Optional<User> findByEmail(String email);
    // Boolean existsByEmail(String email);

    // Métodos para verificación de cuenta y reseteo de contraseña (se mantienen)
    Optional<User> findByVerificationToken(String token);
    Optional<User> findByResetPasswordToken(String token);
}