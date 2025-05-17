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
import java.util.Arrays;
import java.util.List;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private final JWTAuthenticationFilter jwtAuthenticationFilter;
    private final JWTUtil jwtUtil;
    private final UsuarioServicio usuarioServicio; // Cambio aquí
    @Value("${frontend.url}")
    private String frontendUrl;

    @Autowired
    public SecurityConfig(JWTUtil jwtUtil, UsuarioServicio usuarioServicio) { // Cambio aquí
        log.info("Inicializando SecurityConfig...");
        this.jwtUtil = jwtUtil;
        this.usuarioServicio = usuarioServicio;
        this.jwtAuthenticationFilter = new JWTAuthenticationFilter(jwtUtil);
        log.info("JWTAuthenticationFilter creado.");
    }
        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            log.debug("Configurando SecurityFilterChain...");
            http
                    .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                    .csrf(csrf -> csrf.disable())
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .exceptionHandling(handler -> handler
                            .authenticationEntryPoint((request, response, authException) -> {
                                log.warn("AuthenticationEntryPoint triggered. Path: {}, Error: {}", request.getRequestURI(), authException.getMessage());
                                String acceptHeader = request.getHeader("Accept");
                                if (acceptHeader != null && acceptHeader.contains("application/json")) {
                                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                    response.setContentType("application/json");
                                    response.getWriter().write("{\"error\": \"No autorizado\", \"message\": \"" + authException.getMessage() + "\"}");
                                } else {
                                    log.debug("Redirigiendo usuario no autenticado (no JSON) al login frontend.");
                                    response.sendRedirect(frontendUrl + "/login?unauthorized=true");
                                }
                            })
                    )
                    .authorizeHttpRequests(auth -> auth
                            // Rutas públicas
                            .requestMatchers("/", "/index.html", "/static/**", "/favicon.ico", "/manifest.json", "/logo192.png", "/logo512.png").permitAll()
                            .requestMatchers("/api/auth/**").permitAll()
                            .requestMatchers("/oauth2/**").permitAll()
                            .requestMatchers("/login/oauth2/code/google").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/canchas", "/api/canchas/**").permitAll()
                            .requestMatchers("/api/pagos/ipn").permitAll()
                            .requestMatchers("/api/pagos/notificacion").permitAll()  // Permitir acceso sin autenticación
                            .requestMatchers("/error").permitAll()
                            .requestMatchers("/error-404").permitAll()

                            // Rutas autenticadas
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
                                log.error("Error en autenticación OAuth2: {}", exception.getMessage());
                                response.sendRedirect(frontendUrl + "/login?error=oauth_failed");
                            })
                    );
            log.debug("SecurityFilterChain configurado exitosamente.");
            return http.build();
        }

    // Configuración CORS (Bean) - Sin cambios
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(frontendUrl + ""));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }


    // AuthenticationManager Bean - Sin cambios
    @Bean
    public AuthenticationManager authenticationManager(PasswordEncoder passwordEncoder) throws Exception {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(usuarioServicio); // Cambio aquí
        authProvider.setPasswordEncoder(passwordEncoder);
        log.debug("DaoAuthenticationProvider configurado.");
        return new ProviderManager(authProvider);
    }

    // OAuth2 Success Handler Bean (con email añadido a la URL) - Sin cambios respecto al anterior
    @Bean
    public AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler() {
        return (request, response, authentication) -> {
            log.info("--- oAuth2AuthenticationSuccessHandler INICIADO ---");

            if (authentication.getPrincipal() instanceof DefaultOAuth2User oauthUser) {
                log.debug("Principal es DefaultOAuth2User.");
                String email = oauthUser.getAttribute("email");
                String nombre = oauthUser.getAttribute("name");
                log.info("OAuth2 User Info -> Email: {}, Nombre: {}", email, nombre);

                if (email == null || email.isBlank()) {
                    log.error("ERROR: Email obtenido de Google es null o vacío.");
                    response.sendRedirect(frontendUrl + "/login?error=email_null");
                    return;
                }

                try {
                    String token = this.jwtUtil.generateTokenFromEmail(email);
                    log.debug("JWT generado para {} (primeros 10 chars): {}...", email, token.substring(0, 10));

                    String targetUrl = frontendUrl + "/oauth2-success?token=" + token;
                    if (nombre != null && !nombre.isEmpty()) {
                        targetUrl += "&nombre=" + URLEncoder.encode(nombre, StandardCharsets.UTF_8);
                    }
                    targetUrl += "&email=" + URLEncoder.encode(email, StandardCharsets.UTF_8); // Añadir email

                    log.info("Redirigiendo a frontend: {}", targetUrl);
                    response.sendRedirect(targetUrl);
                    log.debug("--- Redirección enviada a frontend ---");

                } catch (Exception e) {
                    log.error("Error al generar JWT o redirigir tras OAuth2: {}", e.getMessage(), e);
                    response.sendRedirect(frontendUrl + "/login?error=handler_exception");
                }

            } else {
                log.warn("Principal NO es DefaultOAuth2User: {}", authentication.getPrincipal().getClass());
                response.sendRedirect(frontendUrl + "/login?error=principal_invalido");
            }
        };
    }

    // PasswordEncoder Bean - Sin cambios
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}