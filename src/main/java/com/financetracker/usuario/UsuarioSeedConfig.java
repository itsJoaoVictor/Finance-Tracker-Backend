package com.financetracker.usuario;

import com.financetracker.usuario.entity.Usuario;
import com.financetracker.usuario.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UsuarioSeedConfig {

    @Bean
    CommandLineRunner seedUsuarios(UsuarioRepository usuarioRepository) {
        return args -> {
            if (!usuarioRepository.existsByEmail("existente@example.com")) {
                Usuario usuario = new Usuario("existente", "existente@example.com", "SenhaForte123!");
                usuarioRepository.save(usuario);
            }
        };
    }
}