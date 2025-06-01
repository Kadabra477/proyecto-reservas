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

    @Value("${frontend.url}")
    private String frontendUrl;

    private final JWTAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JWTUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
        this.jwtAuthenticationFilter = new JWTAuthenticationFilter(jwtUtil);
        log.info("SecurityConfig initialized.");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
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
                        // Rutas públicas que no requieren autenticación
                        .requestMatchers(
                                "/", "/index.html", "/static/**", "/favicon.ico", "/manifest.json",
                                "/logo192.png", "/logo512.png",
                                "/api/auth/**", "/oauth2/**", "/login/oauth2/code/google",
                                "/api/pagos/ipn", "/api/pagos/notificacion",
                                "/error", "/error-404"
                        ).permitAll()
                        // Rutas GET específicas permitidas
                        .requestMatchers(HttpMethod.GET, "/api/canchas", "/api/canchas/**").permitAll()

                        // Rutas protegidas que requieren autenticación
                        .requestMatchers(HttpMethod.GET, "/api/users/me").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/users/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/users/me/profile-picture").authenticated()
                        .requestMatchers("/api/reservas/**").authenticated()
                        .requestMatchers("/api/pagos/crear-preferencia/**").authenticated()
                        .requestMatchers("/api/pagos/pdf/**").authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // NUEVO: Proteger el endpoint de estadísticas para el rol ADMIN
                        .requestMatchers("/api/estadisticas/admin").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
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
        // Asegúrate de que todas tus URLs de frontend (localhost, Vercel, etc.) estén aquí
        config.setAllowedOrigins(List.of(frontendUrl, "https://proyecto-reservas-jsw5.onrender.com", "http://localhost:3000"));
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
                String nombre = oauthUser.getAttribute("name");
                String role = oauthUser.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .filter(a -> a.startsWith("ROLE_"))
                        .map(a -> a.substring(5))
                        .findFirst()
                        .orElse("USER");

                if (email == null || email.isBlank()) {
                    log.error("OAuth2 user email is null or blank");
                    response.sendRedirect(frontendUrl + "/login?error=email_null");
                    return;
                }

                try {
                    String token = jwtUtil.generateTokenFromEmail(email);
                    String targetUrl = frontendUrl + "/oauth2/redirect?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8); // Ajuste aquí la ruta de redirección del frontend para OAuth2
                    if (nombre != null && !nombre.isEmpty()) {
                        targetUrl += "&name=" + URLEncoder.encode(nombre, StandardCharsets.UTF_8);
                    }
                    targetUrl += "&username=" + URLEncoder.encode(email, StandardCharsets.UTF_8);
                    targetUrl += "&role=" + URLEncoder.encode(role, StandardCharsets.UTF_8);

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