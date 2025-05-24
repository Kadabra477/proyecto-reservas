package com.example.reservafutbol.Configuracion;

import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.example.reservafutbol.Servicio.UsuarioServicio;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final JWTUtil jwtUtil;

    @Value("${frontend.url}")
    private String frontendUrl;

    private final JWTAuthenticationFilter jwtAuthenticationFilter;

    // Constructor solo con lo mínimo para evitar ciclo
    public SecurityConfig(JWTUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
        this.jwtAuthenticationFilter = new JWTAuthenticationFilter(jwtUtil);
        log.info("SecurityConfig initialized.");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
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
                        .requestMatchers("/", "/index.html", "/static/**", "/favicon.ico", "/manifest.json", "/logo192.png", "/logo512.png").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/oauth2/**").permitAll()
                        .requestMatchers("/login/oauth2/code/google").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/canchas", "/api/canchas/**").permitAll()
                        .requestMatchers("/api/pagos/ipn").permitAll()
                        .requestMatchers("/api/pagos/notificacion").permitAll()
                        .requestMatchers("/error", "/error-404").permitAll()
                        .requestMatchers("/api/reservas/**").authenticated()
                        .requestMatchers("/api/pagos/crear-preferencia/**").authenticated()
                        .requestMatchers("/api/pagos/pdf/**").authenticated()
                        .requestMatchers("/api/usuarios/mi-perfil").authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
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
        config.setAllowedOrigins(List.of(frontendUrl));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // Aquí inyectamos UsuarioServicio y PasswordEncoder, no en el constructor
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

                if (email == null || email.isBlank()) {
                    log.error("OAuth2 user email is null or blank");
                    response.sendRedirect(frontendUrl + "/login?error=email_null");
                    return;
                }

                try {
                    String token = jwtUtil.generateTokenFromEmail(email);
                    String targetUrl = frontendUrl + "/oauth2-success?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
                    if (nombre != null && !nombre.isEmpty()) {
                        targetUrl += "&nombre=" + URLEncoder.encode(nombre, StandardCharsets.UTF_8);
                    }
                    targetUrl += "&email=" + URLEncoder.encode(email, StandardCharsets.UTF_8);

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
