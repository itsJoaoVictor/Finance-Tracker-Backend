package com.financetracker.ia.repository;

import com.financetracker.ia.domain.IaDicionarioCategoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IaDicionarioCategoriaRepository extends JpaRepository<IaDicionarioCategoria, String> {
}
