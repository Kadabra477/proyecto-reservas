package com.example.reservafutbol.Configuracion;

import com.example.reservafutbol.Modelo.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JWTUtil {

    private static final Logger log = LoggerFactory.getLogger(JWTUtil.class);

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration.ms}")
    private int jwtExpirationMs;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateJwtToken(Authentication authentication) {
        User userPrincipal = (User) authentication.getPrincipal();

        return Jwts.builder()
                .setSubject(userPrincipal.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .claim("roles", userPrincipal.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList()))
                .claim("nombreCompleto", userPrincipal.getNombreCompleto())
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // MODIFICADO: Este método fue corregido en tu código
    public String generateTokenFromEmail(String email, String nombreCompleto, String role) {
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .claim("roles", Collections.singletonList("ROLE_" + role))
                .claim("nombreCompleto", nombreCompleto)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // NUEVO MÉTODO: Genera un token a partir de un objeto User
    public String generateTokenFromUser(User user) {
        return Jwts.builder()
                .setSubject(user.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .claim("roles", user.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList()))
                .claim("nombreCompleto", user.getNombreCompleto())
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String getUserNameFromJwtToken(String token) {
        return parseClaims(token).getSubject();
    }

    public String getUserRoleFromJwtToken(String token) {
        Claims claims = parseClaims(token);
        List<String> roles = claims.get("roles", List.class);
        if (roles != null && !roles.isEmpty()) {
            String fullRole = roles.get(0);
            return fullRole.replace("ROLE_", "");
        }
        return null;
    }

    public String getNombreCompletoFromJwtToken(String token) {
        Claims claims = parseClaims(token);
        return claims.get("nombreCompleto", String.class);
    }

    public boolean validateJwtToken(String authToken) {
        try {
            parseClaims(authToken);
            return true;
        } catch (MalformedJwtException e) {
            log.error("Token JWT inválido: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("Token JWT expirado: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("Token JWT no soportado: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("Cadena de claims JWT vacía: {}", e.getMessage());
        } catch (SignatureException e) {
            log.error("Firma JWT inválida: {}", e.getMessage());
        }
        return false;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}