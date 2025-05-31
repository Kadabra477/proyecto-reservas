package com.example.reservafutbol.Configuracion;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException; // Importar
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException; // Importar
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SignatureException; // Importar
import io.jsonwebtoken.UnsupportedJwtException; // Importar
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
        } catch (ExpiredJwtException e) {
            System.err.println(">>> ERROR al validar token: Token expirado. Mensaje=" + e.getMessage());
            // No se necesita printStackTrace completo para errores de expiración, son comunes.
            return false;
        } catch (UnsupportedJwtException | MalformedJwtException | SignatureException | IllegalArgumentException e) {
            // Loguear el error específico para otros tipos de fallos en el token
            System.err.println(">>> ERROR al validar token: Tipo=" + e.getClass().getName() + ", Mensaje=" + e.getMessage());
            // Para estos tipos de errores, un printStackTrace completo puede ser útil en desarrollo.
            // Considera usar un logger (slf4j) y configurar el nivel de log.
            // e.printStackTrace(); // <-- Quitar en producción para no llenar logs
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