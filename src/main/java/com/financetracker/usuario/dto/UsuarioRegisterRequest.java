package com.financetracker.usuario.dto;

public record UsuarioRegisterRequest(String email, String password, String confirmPassword) {
}