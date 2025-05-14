package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Repositorio.UsuarioRepositorio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.User.UserBuilder; // Puedes importar el builder directamente
import org.springframework.stereotype.Service;


@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UsuarioRepositorio usuarioRepositorio;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        com.example.reservafutbol.Modelo.User usuario = usuarioRepositorio.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con username: " + username));
        UserBuilder builder = org.springframework.security.core.userdetails.User.builder();
        builder.username(usuario.getUsername());
        builder.password(usuario.getPassword());
        builder.roles(usuario.getRol());
        return builder.build();
    }
}
