package com.example.reservafutbol.Configuracion;

import com.example.reservafutbol.Servicio.UsuarioServicio;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final JWTUtil jwtUtil;
    private final UsuarioServicio usuarioServicio;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Value("${BACKEND_URL}")
    private String backendUrlBase;

    public SecurityConfig(JWTUtil jwtUtil, UsuarioServicio usuarioServicio) {
        this.jwtUtil = jwtUtil;
        this.usuarioServicio = usuarioServicio;
        log.info("SecurityConfig initialized with JWTUtil and UsuarioServicio.");
    }

    @Bean
    public JWTAuthenticationFilter authenticationJwtTokenFilter() {
        return new JWTAuthenticationFilter(jwtUtil, usuarioServicio);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // Corregido: .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handler -> handler.authenticationEntryPoint((request, response, authException) -> {
                    log.warn("Unauthorized access to '{}': {}", request.getRequestURI(), authException.getMessage());
                    String accept = request.getHeader("Accept");
                    if (accept != null && accept.contains("application/json")) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\": \"No autorizado\", \"message\": \"" + authException.getMessage() + "\"}");
                    } else {
                        response.sendRedirect(frontendUrl + "/login?unauthorized=true");
                    }
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/index.html", "/static/**", "/favicon.ico", "/manifest.json",
                                "/logo192.png", "/logo512.png",
                                "/api/auth/**", "/oauth2/**", "/login/oauth2/code/google",
                                "/api/pagos/ipn", "/api/pagos/notificacion",
                                "/error", "/error-404"
                        ).permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/complejos", "/api/complejos/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reservas/disponibilidad-por-tipo").permitAll()

                        .requestMatchers("/api/complejos/mis-complejos").hasAnyRole("ADMIN", "COMPLEX_OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/complejos").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/complejos/**").hasAnyRole("ADMIN", "COMPLEX_OWNER")
                        .requestMatchers(HttpMethod.DELETE, "/api/complejos/**").hasAnyRole("ADMIN", "COMPLEX_OWNER")

                        .requestMatchers("/api/reservas/crear").authenticated()
                        .requestMatchers("/api/reservas/usuario").authenticated()

                        // Rutas de Reservas para Admin o Propietario (o ambas, el servicio filtra)
                        .requestMatchers("/api/reservas/admin/todas").hasAnyRole("ADMIN", "COMPLEX_OWNER")
                        .requestMatchers("/api/reservas/{id}").hasAnyRole("ADMIN", "COMPLEX_OWNER", "USER")
                        .requestMatchers("/api/reservas/{id}/pdf-comprobante").hasAnyRole("ADMIN", "COMPLEX_OWNER", "USER")
                        .requestMatchers("/api/reservas/{id}/confirmar").hasAnyRole("ADMIN", "COMPLEX_OWNER")
                        .requestMatchers("/api/reservas/{id}/marcar-pagada").hasAnyRole("ADMIN", "COMPLEX_OWNER")
                        .requestMatchers("/api/reservas/{id}/equipos").hasAnyRole("ADMIN", "COMPLEX_OWNER")
                        .requestMatchers(HttpMethod.DELETE, "/api/reservas/{id}").hasAnyRole("ADMIN", "COMPLEX_OWNER")
                        .requestMatchers(HttpMethod.GET, "/api/reservas/complejo/**").hasAnyRole("ADMIN", "COMPLEX_OWNER") // Este es el endpoint complejoId/tipoCancha

                        .requestMatchers("/api/pagos/crear-preferencia/**").authenticated()

                        .requestMatchers("/api/users/me").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/users/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/users/me/profile-picture").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/users").hasRole("ADMIN") // Solo ADMIN puede listar todos los usuarios

                        .requestMatchers("/api/estadisticas/admin").hasAnyRole("ADMIN", "COMPLEX_OWNER") // Mantenemos esta

                        .anyRequest().authenticated()
                )
                .addFilterBefore(authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class)
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oAuth2AuthenticationSuccessHandler())
                        .failureHandler((request, response, exception) -> {
                            log.error("OAuth2 authentication failure: {}", exception.getMessage());
                            response.sendRedirect(frontendUrl + "/login?error=oauth_failed");
                        })
                );

        log.info("SecurityFilterChain configured.");
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(frontendUrl, backendUrlBase, "http://localhost:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(UsuarioServicio usuarioServicio, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(usuarioServicio);
        provider.setPasswordEncoder(passwordEncoder);
        log.info("DaoAuthenticationProvider configured.");
        return new ProviderManager(provider);
    }

    @Bean
    public AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler() {
        return (request, response, authentication) -> {
            log.info("OAuth2 authentication success handler triggered.");

            if (authentication.getPrincipal() instanceof DefaultOAuth2User oauthUser) {
                String email = oauthUser.getAttribute("email");
                String nombreCompleto = oauthUser.getAttribute("name");
                String role = authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .filter(a -> a.startsWith("ROLE_"))
                        .map(a -> a.substring(5))
                        .findFirst()
                        .orElse("USER"); // Si no tiene un rol específico, default a USER

                if (email == null || email.isBlank()) {
                    log.error("OAuth2 user email is null or blank");
                    response.sendRedirect(frontendUrl + "/login?error=email_null");
                    return;
                }

                try {
                    String token = jwtUtil.generateTokenFromEmail(email, nombreCompleto, role); // Asegura que 'role' aquí es String
                    String targetUrl = frontendUrl + "/oauth2/redirect?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);

                    targetUrl += "&name=" + URLEncoder.encode(nombreCompleto != null ? nombreCompleto : "", StandardCharsets.UTF_8);
                    targetUrl += "&username=" + URLEncoder.encode(email, StandardCharsets.UTF_8);
                    targetUrl += "&role=" + URLEncoder.encode(role, StandardCharsets.UTF_8); // Pasa el rol como string

                    log.info("Redirecting OAuth2 user to frontend URL: {}", targetUrl);
                    response.sendRedirect(targetUrl);
                } catch (Exception e) {
                    log.error("Error generating JWT or redirecting: {}", e.getMessage(), e);
                    response.sendRedirect(frontendUrl + "/login?error=handler_exception");
                }
            } else {
                log.warn("OAuth2 principal is not DefaultOAuth2User: {}", authentication.getPrincipal().getClass());
                response.sendRedirect(frontendUrl + "/login?error=principal_invalido");
            }
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}