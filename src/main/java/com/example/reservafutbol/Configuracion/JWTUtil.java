package com.example.reservafutbol.Configuracion;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JWTUtil {

    private final String secretKey = "secret";

    public String generateTokenFromEmail(String email) {
        return Jwts.builder()
                .setSubject(email)
                .claim("role", "USER") // Rol por defecto
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60)) // 1 hora
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();
    }

    // ✅ Generar token con username + rol
    public String generateToken(String username, String role) {
        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)  // 👈 importante
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60)) // 1 hora
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();
    }
    public boolean isTokenValid(String token) {
        try {
            Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token);
            // Si llega aquí, es válido
            System.out.println(">>> JWT VÁLIDO para token que empieza con: " + (token != null && token.length() > 10 ? token.substring(0, 10) : "N/A")); // Log de éxito
            return true;
        } catch (Exception e) {
            // ¡Loguear el error específico!
            System.err.println(">>> ERROR al validar token: Tipo=" + e.getClass().getName() + ", Mensaje=" + e.getMessage());
            e.printStackTrace(); // <-- AÑADE ESTO para ver toda la traza del error
            return false;
        }
    }

    // ✅ Extraer el nombre de usuario del token
    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    // ✅ Extraer el rol del token
    public String extractRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    // ✅ Método interno para obtener claims
    private Claims getClaims(String token) {
        return Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody();
    }
}