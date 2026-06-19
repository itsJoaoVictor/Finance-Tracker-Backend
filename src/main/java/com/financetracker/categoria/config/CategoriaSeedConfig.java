package com.financetracker.categoria.config;

import com.financetracker.categoria.entity.Categoria;
import com.financetracker.categoria.repository.CategoriaRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class CategoriaSeedConfig {

    @Bean
    CommandLineRunner seedCategorias(CategoriaRepository categoriaRepository) {
        return args -> {
            // Conta quantas categorias globais (usuario_id IS NULL) existem no banco
            long countGlobais = categoriaRepository.findAll().stream()
                    .filter(c -> c.getUsuario() == null)
                    .count();

            if (countGlobais == 0) {
                List<Categoria> globais = List.of(
                        criarCategoriaGlobal("Alimentação", "shopping-basket", "#FF9F43"),
                        criarCategoriaGlobal("Transporte", "car", "#0984E3"),
                        criarCategoriaGlobal("Moradia", "home", "#E84118"),
                        criarCategoriaGlobal("Saúde", "heartbeat", "#2ED573"),
                        criarCategoriaGlobal("Educação", "graduation-cap", "#9B59B6"),
                        criarCategoriaGlobal("Lazer", "smile", "#F1C40F"),
                        criarCategoriaGlobal("Outros", "help-circle", "#7F8C8D")
                );
                categoriaRepository.saveAll(globais);
            }
        };
    }

    private Categoria criarCategoriaGlobal(String nome, String icone, String corHexadecimal) {
        Categoria categoria = new Categoria();
        categoria.setNome(nome);
        categoria.setIcone(icone);
        categoria.setCorHexadecimal(corHexadecimal);
        categoria.setUsuario(null);
        categoria.setAtivo(true);
        return categoria;
    }
}
