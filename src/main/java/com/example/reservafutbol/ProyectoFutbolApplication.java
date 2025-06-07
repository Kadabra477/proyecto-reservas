package com.example.reservafutbol;

import com.example.reservafutbol.Modelo.ERole; // Importar
import com.example.reservafutbol.Modelo.Role;   // Importar
import com.example.reservafutbol.Repositorio.RoleRepositorio; // Importar
import org.springframework.boot.CommandLineRunner; // Importar
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean; // Importar
import org.slf4j.Logger;
import org.slf4j.LoggerFactory; // Importar

@SpringBootApplication
public class ProyectoFutbolApplication {

	private static final Logger log = LoggerFactory.getLogger(ProyectoFutbolApplication.class); // Logger para la clase principal

	public static void main(String[] args) {
		SpringApplication.run(ProyectoFutbolApplication.class, args);
	}

	// NUEVO BEAN: Inicializa los roles en la base de datos si no existen
	@Bean
	public CommandLineRunner initRoles(RoleRepositorio roleRepositorio) {
		return args -> {
			log.info("Iniciando verificación e inserción de roles por defecto...");

			// Verificar y crear ROLE_USER
			if (roleRepositorio.findByName(ERole.ROLE_USER).isEmpty()) {
				roleRepositorio.save(new Role(ERole.ROLE_USER));
				log.info("Rol ROLE_USER insertado.");
			} else {
				log.info("Rol ROLE_USER ya existe.");
			}

			// Verificar y crear ROLE_ADMIN
			if (roleRepositorio.findByName(ERole.ROLE_ADMIN).isEmpty()) {
				roleRepositorio.save(new Role(ERole.ROLE_ADMIN));
				log.info("Rol ROLE_ADMIN insertado.");
			} else {
				log.info("Rol ROLE_ADMIN ya existe.");
			}
			log.info("Verificación de roles completada.");
		};
	}
}