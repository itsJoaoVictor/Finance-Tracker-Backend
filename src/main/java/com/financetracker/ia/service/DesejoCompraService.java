package com.financetracker.ia.service;

import com.financetracker.ia.dto.DesejoCompraDTO;
import com.financetracker.ia.dto.DesejoCompraRequest;
import com.financetracker.ia.entity.DesejoCompra;
import com.financetracker.ia.repository.DesejoCompraRepository;
import com.financetracker.usuario.entity.Usuario;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DesejoCompraService {

    private final DesejoCompraRepository repository;

    public DesejoCompraService(DesejoCompraRepository repository) {
        this.repository = repository;
    }

    public List<DesejoCompraDTO> listarDesejos(Usuario usuario) {
        return repository.findByUsuarioIdOrderByCriadoEmDesc(usuario.getId())
                .stream()
                .map(d -> new DesejoCompraDTO(d.getId(), d.getNome(), d.getValor()))
                .collect(Collectors.toList());
    }

    public DesejoCompraDTO criarDesejo(Usuario usuario, DesejoCompraRequest request) {
        DesejoCompra desejo = new DesejoCompra(usuario, request.nome(), request.valor());
        desejo = repository.save(desejo);
        return new DesejoCompraDTO(desejo.getId(), desejo.getNome(), desejo.getValor());
    }

    public DesejoCompraDTO atualizarDesejo(Usuario usuario, UUID id, DesejoCompraRequest request) {
        DesejoCompra desejo = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Desejo de compra não encontrado."));
        
        if (!desejo.getUsuario().getId().equals(usuario.getId())) {
            throw new RuntimeException("Acesso negado.");
        }

        desejo.setNome(request.nome());
        desejo.setValor(request.valor());
        desejo = repository.save(desejo);

        return new DesejoCompraDTO(desejo.getId(), desejo.getNome(), desejo.getValor());
    }

    public void deletarDesejo(Usuario usuario, UUID id) {
        DesejoCompra desejo = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Desejo de compra não encontrado."));

        if (!desejo.getUsuario().getId().equals(usuario.getId())) {
            throw new RuntimeException("Acesso negado.");
        }

        repository.delete(desejo);
    }
}
