package com.example.reservafutbol;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootApplication
public class ProyectoFutbolApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProyectoFutbolApplication.class, args);
	}

	@Bean
	public ApplicationRunner applicationRunner(ApplicationContext ctx) {
		return args -> {
			System.out.println("=== ENDPOINTS DISPONIBLES ===");
			RequestMappingHandlerMapping mapping = ctx.getBean(RequestMappingHandlerMapping.class);
			mapping.getHandlerMethods().forEach((key, value) -> {
				System.out.println(key + " => " + value);
			});
		};
	}
}
