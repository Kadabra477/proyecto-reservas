package com.example.reservafutbol.Configuracion;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Configuration
@EnableWebMvc // Habilita la configuración MVC de Spring
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Regla explícita para asegurar que las rutas /api/** NO sean tratadas como recursos estáticos.
        // Si una URL comienza con /api/, este manejador intentará resolverla, pero nuestro resolver personalizado
        // devolverá null, indicando que no es un recurso estático, permitiendo que el DispatcherServlet la mapee a un controlador.
        registry.addResourceHandler("/api/**")
                .addResourceLocations("classpath:/static/") // La ubicación no importa mucho aquí, es el resolver quien decide
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected org.springframework.core.io.Resource getResource(String resourcePath,
                                                                               org.springframework.core.io.Resource location) throws java.io.IOException {
                        // Si la ruta comienza con "api/", significa que es una llamada a la API y no un recurso estático.
                        // Devolvemos null para que Spring continúe buscando un controlador REST para esta ruta.
                        if (resourcePath.startsWith("api/")) {
                            return null;
                        }
                        return super.getResource(resourcePath, location);
                    }
                });

        // Regla general para servir todos los demás recursos estáticos de tu aplicación (frontend, imágenes, etc.).
        // Esto incluirá "/", "/index.html", "/static/**", "/favicon.ico", etc.
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/", "classpath:/public/", "classpath:/META-INF/resources/")
                .setCachePeriod(3600) // Configura el período de caché para recursos estáticos (ej. 1 hora)
                .resourceChain(true)
                .addResolver(new PathResourceResolver());
    }
}