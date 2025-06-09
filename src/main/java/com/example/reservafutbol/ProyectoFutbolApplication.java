package com.example.reservafutbol;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
public class ProyectoFutbolApplication {

	private static final Logger log = LoggerFactory.getLogger(ProyectoFutbolApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(ProyectoFutbolApplication.class, args);
	}
}
