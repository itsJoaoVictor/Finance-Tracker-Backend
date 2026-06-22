package com.financetracker.categoria;

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
            // Seed global categories (where usuario_id is null)
            List<Categoria> globais = categoriaRepository.findAllByUsuarioId(null);
            
            seedIfNotExist(globais, "Alimentação", "shopping-basket", "#FF9F43", categoriaRepository);
            seedIfNotExist(globais, "Transporte", "car", "#0984E3", categoriaRepository);
            seedIfNotExist(globais, "Moradia", "home", "#E84118", categoriaRepository);
            seedIfNotExist(globais, "Saúde", "heartbeat", "#2ED573", categoriaRepository);
            seedIfNotExist(globais, "Educação", "graduation-cap", "#9B59B6", categoriaRepository);
            seedIfNotExist(globais, "Lazer", "smile", "#F1C40F", categoriaRepository);
            seedIfNotExist(globais, "Outros", "help-circle", "#7F8C8D", categoriaRepository);
        };
    }

    private void seedIfNotExist(List<Categoria> globais, String nome, String icone, String corHexadecimal, CategoriaRepository repo) {
        boolean exists = globais.stream().anyMatch(c -> c.getNome().equalsIgnoreCase(nome));
        if (!exists) {
            Categoria categoria = new Categoria(null, nome, icone, corHexadecimal, true);
            repo.save(categoria);
        }
    }
}
