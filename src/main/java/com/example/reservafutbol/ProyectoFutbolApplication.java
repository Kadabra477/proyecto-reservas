package com.example.reservafutbol;

import com.example.reservafutbol.Modelo.ERole;
import com.example.reservafutbol.Modelo.Role;
import com.example.reservafutbol.Repositorio.RoleRepositorio;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean; // Importa @Bean

@SpringBootApplication
public class ProyectoFutbolApplication {

	private static final Logger log = LoggerFactory.getLogger(ProyectoFutbolApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(ProyectoFutbolApplication.class, args);
	}

	// Método para inicializar roles al inicio de la aplicación
	@Bean // Marca este método como un bean gestionado por Spring
	public CommandLineRunner initData(RoleRepositorio roleRepositorio) {
		return args -> {
			log.info("Verificando y creando roles si es necesario...");

			if (roleRepositorio.findByName(ERole.ROLE_USER).isEmpty()) {
				roleRepositorio.save(new Role(ERole.ROLE_USER));
				log.info("Rol ROLE_USER creado.");
			}
			if (roleRepositorio.findByName(ERole.ROLE_ADMIN).isEmpty()) {
				roleRepositorio.save(new Role(ERole.ROLE_ADMIN));
				log.info("Rol ROLE_ADMIN creado.");
			}
			if (roleRepositorio.findByName(ERole.ROLE_COMPLEX_OWNER).isEmpty()) {
				roleRepositorio.save(new Role(ERole.ROLE_COMPLEX_OWNER));
				log.info("Rol ROLE_COMPLEX_OWNER creado.");
			}
			log.info("Verificación de roles completada.");
		};
	}
}