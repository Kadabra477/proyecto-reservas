package com.example.reservafutbol.DTO;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AsignarRolesRequest {
    private List<String> roles; // Lista de nombres de roles (ej. ["ROLE_USER", "ROLE_COMPLEX_OWNER"])
}