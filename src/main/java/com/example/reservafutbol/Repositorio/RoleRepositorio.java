package com.example.reservafutbol.Repositorio;

import com.example.reservafutbol.Modelo.ERole;
import com.example.reservafutbol.Modelo.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RoleRepositorio extends JpaRepository<Role, Long> {
    Optional<Role> findByName(ERole name);
}
