package com.example.reservafutbol.payload.request;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String email;
    private String password;
    private String nombreCompleto;
    private String ubicacion;
}
