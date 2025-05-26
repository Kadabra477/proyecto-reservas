package com.example.reservafutbol.Configuracion;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Configuration
@EnableWebMvc // Es importante mantener esta anotación
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Esta es la regla MÁS CRÍTICA:
        // Asegurarse de que cualquier ruta que comience con "/api/" NO sea tratada como un recurso estático.
        // Esto fuerza a Spring a pasar la solicitud al DispatcherServlet para que busque un @RestController.
        registry.addResourceHandler("/api/**")
                .resourceChain(false) // No continuar la cadena de recursos si este manejador no encuentra nada
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected org.springframework.core.io.Resource getResource(String resourcePath,
                                                                               org.springframework.core.io.Resource location) throws java.io.IOException {
                        // Si la ruta solicitada comienza con "api/", significa que es una llamada a nuestra API REST.
                        // En este caso, NO es un recurso estático, por lo que devolvemos 'null'.
                        // Esto le indica a Spring que este ResourceHandler no puede resolver la ruta,
                        // permitiendo que el DispatcherServlet tome el control y la dirija al controlador adecuado.
                        if (resourcePath.startsWith("api/")) {
                            return null;
                        }
                        // Para otras rutas, se usa el comportamiento estándar de ResourceResolver
                        return super.getResource(resourcePath, location);
                    }
                });

        // Esta es la regla general para servir todos los demás recursos estáticos de tu aplicación.
        // Aquí es donde se encontrarán tu index.html, CSS, JS, etc.
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/", "classpath:/public/", "classpath:/META-INF/resources/")
                .setCachePeriod(3600) // 1 hora de caché, puedes ajustar esto
                .resourceChain(true)
                .addResolver(new PathResourceResolver());
    }

    // No necesitas anular configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer)
    // a menos que estés teniendo un problema específico con el default servlet.
}