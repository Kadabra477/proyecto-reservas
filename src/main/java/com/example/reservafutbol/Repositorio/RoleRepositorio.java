package com.example.reservafutbol.Repositorio;

import com.example.reservafutbol.Modelo.Role;
import com.example.reservafutbol.Modelo.ERole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepositorio extends JpaRepository<Role, Long> {
    Optional<Role> findByName(ERole name);
}