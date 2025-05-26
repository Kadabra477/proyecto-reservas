package com.example.reservafutbol.Configuracion;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Configuration
@EnableWebMvc // Importante para controlar el comportamiento MVC
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Esta es la regla MÁS CRÍTICA Y EXPLÍCITA.
        // Primero, asegúrate de que NINGÚN recurso estático sea servido desde /api/**.
        // Al no definir un .addResourceLocations() para /api/** aquí,
        // y al usar resourceChain(false) + PathResourceResolver que devuelve null,
        // garantizamos que estas rutas sean ignoradas por el ResourceHttpRequestHandler
        // y pasen directamente al DispatcherServlet.
        registry.addResourceHandler("/api/**")
                .resourceChain(false) // No continuar la cadena de resolución si no se encuentra
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected org.springframework.core.io.Resource getResource(String resourcePath,
                                                                               org.springframework.core.io.Resource location) throws java.io.IOException {
                        // Devolver null explícitamente para cualquier ruta que comience con "api/".
                        // Esto fuerza a Spring a que esta solicitud no es un recurso estático.
                        return null;
                    }
                });

        // Luego, configura el manejador de recursos estáticos normal para el resto de tus archivos.
        // Esto servirá tu index.html, JS, CSS, imágenes directamente desde la raíz del servidor web.
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/", "classpath:/public/", "classpath:/META-INF/resources/")
                .setCachePeriod(3600) // 1 hora de caché para recursos estáticos
                .resourceChain(true)
                .addResolver(new PathResourceResolver());
    }

    // Es importante NO anular configureDefaultServletHandling si no es necesario,
    // ya que a veces puede crear conflictos adicionales con el DispatcherServlet.
}