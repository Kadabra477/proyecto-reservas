package com.example.reservafutbol.Configuracion;

import com.example.reservafutbol.Servicio.UsuarioServicio;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JWTAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JWTAuthenticationFilter.class);
    private final JWTUtil jwtUtil;
    private final UsuarioServicio userDetailsService; // UsuarioServicio es tu UserDetailsService

    public JWTAuthenticationFilter(JWTUtil jwtUtil, UsuarioServicio userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = parseJwt(request);

            if (jwt != null && jwtUtil.validateJwtToken(jwt)) {
                String username = jwtUtil.getUserNameFromJwtToken(jwt);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    // Carga los detalles completos del usuario desde la base de datos
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    // Si userDetails no es null (es decir, el usuario existe en la DB)
                    if (userDetails != null) {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails,
                                        null, // Credenciales (null para JWT después de la validación)
                                        userDetails.getAuthorities()); // Autoridades/Roles del usuario

                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } else {
                        log.warn("Usuario {} no encontrado en la base de datos después de la validación del JWT.", username);
                    }
                }
            }
        } catch (Exception e) {
            log.error("No se pudo establecer la autenticación del usuario (Error JWT/Filtro): {}", e.getMessage(), e);
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");

        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }

        return null;
    }
}