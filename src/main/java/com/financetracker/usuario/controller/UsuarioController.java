package com.financetracker.usuario.controller;

import com.financetracker.usuario.dto.LoginRequest;
import com.financetracker.usuario.dto.LoginResponse;
import com.financetracker.usuario.dto.UsuarioRegisterRequest;
import com.financetracker.usuario.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

	private final UsuarioService usuarioService;

	public UsuarioController(UsuarioService usuarioService) {
		this.usuarioService = usuarioService;
	}

	@PostMapping("/register")
	public ResponseEntity<Void> register(@Valid @RequestBody UsuarioRegisterRequest request) {
		usuarioService.register(request);
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		LoginResponse response = usuarioService.login(request);
		if (Boolean.TRUE.equals(response.twoFactorRequired())) {
			return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
		}
		return ResponseEntity.ok(response);
	}
}
