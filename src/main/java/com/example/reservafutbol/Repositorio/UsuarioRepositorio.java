package com.example.reservafutbol.Repositorio;

import com.example.reservafutbol.Modelo.User;
import com.example.reservafutbol.Modelo.Role; // ¡Importante! Asegúrate de importar la clase Role
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepositorio extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Boolean existsByUsername(String username);
    Optional<User> findById(Long id);
    Optional<User> findByVerificationToken(String token);
    Optional<User> findByResetPasswordToken(String token);
    List<User> findAllByEnabled(boolean enabled);
    List<User> findByRolesContaining(Role role);
}