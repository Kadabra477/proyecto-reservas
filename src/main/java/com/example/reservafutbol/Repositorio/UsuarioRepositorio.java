package com.example.reservafutbol.Repositorio;

import com.example.reservafutbol.Modelo.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepositorio extends JpaRepository<User, Long> {
    // Métodos esenciales para el login y registro
    Optional<User> findByUsername(String username); // Para buscar por username (ej. para login)
    Boolean existsByUsername(String username); // Para verificar si un username ya existe

    Optional<User> findByEmail(String email); // Para buscar por email (ej. para reseteo de contraseña)
    Boolean existsByEmail(String email); // Para verificar si un email ya existe

    // Métodos para verificación de cuenta y reseteo de contraseña (basados en el modelo User.java)
    Optional<User> findByVerificationToken(String token); // Para activar cuenta con token de verificación
    Optional<User> findByResetPasswordToken(String token); // Para validar token de reseteo de contraseña
}