package com.example.reservafutbol;

import com.example.reservafutbol.Modelo.ERole;
import com.example.reservafutbol.Modelo.Role;
import com.example.reservafutbol.Repositorio.RoleRepositorio;
import org.springframework.boot.CommandLineRunner; // Importar
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean; // Importar
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
public class ProyectoFutbolApplication {

	private static final Logger log = LoggerFactory.getLogger(ProyectoFutbolApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(ProyectoFutbolApplication.class, args);
	}

	@Bean
	public CommandLineRunner initRoles(RoleRepositorio roleRepositorio) {
		return args -> {
			log.info("Iniciando verificación e inserción de roles por defecto...");

			// Verificar y crear ROLE_USER
			if (roleRepositorio.findByName(ERole.ROLE_USER).isEmpty()) {
				roleRepositorio.save(new Role(ERole.ROLE_USER));
				log.info("Rol ROLE_USER creado.");
			} else {
				log.info("Rol ROLE_USER ya existe.");
			}

			// Verificar y crear ROLE_ADMIN
			if (roleRepositorio.findByName(ERole.ROLE_ADMIN).isEmpty()) {
				roleRepositorio.save(new Role(ERole.ROLE_ADMIN));
				log.info("Rol ROLE_ADMIN creado.");
			} else {
				log.info("Rol ROLE_ADMIN ya existe.");
			}

			// --- AÑADIR ESTO: Verificar y crear ROLE_COMPLEX_OWNER ---
			if (roleRepositorio.findByName(ERole.ROLE_COMPLEX_OWNER).isEmpty()) {
				roleRepositorio.save(new Role(ERole.ROLE_COMPLEX_OWNER));
				log.info("Rol ROLE_COMPLEX_OWNER creado.");
			} else {
				log.info("Rol ROLE_COMPLEX_OWNER ya existe.");
			}
			// --- FIN AÑADIR ESTO ---

			log.info("Verificación de roles completada.");
		};
	}
}
