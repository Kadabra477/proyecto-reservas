package com.example.reservafutbol.Configuracion;

import io.github.cdimascio.dotenv.Dotenv;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class DotenvLoader {
    @PostConstruct
    public void init() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(entry ->
                System.setProperty(entry.getKey(), entry.getValue())
        );
    }
}
