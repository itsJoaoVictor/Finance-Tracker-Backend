package com.financetracker.usuario.controller;

import com.financetracker.usuario.dto.LoginRequest;
import com.financetracker.usuario.dto.LoginResponse;
import com.financetracker.usuario.dto.UsuarioRegisterRequest;
import com.financetracker.usuario.dto.UsuarioResponse;
import com.financetracker.usuario.dto.UsuarioUpdateRequest;
import com.financetracker.usuario.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

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

	@GetMapping("/me")
	public ResponseEntity<UsuarioResponse> getMe() {
		UsuarioResponse response = usuarioService.getMe();
		return ResponseEntity.ok(response);
	}

	@GetMapping("/{id}")
	public ResponseEntity<UsuarioResponse> getById(@PathVariable String id) {
		UUID uuid;
		try {
			uuid = UUID.fromString(id);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario nao encontrado");
		}
		UsuarioResponse response = usuarioService.getById(uuid);
		return ResponseEntity.ok(response);
	}

	@PutMapping("/{id}")
	public ResponseEntity<Void> update(@PathVariable String id, @Valid @RequestBody UsuarioUpdateRequest request) {
		UUID uuid;
		try {
			uuid = UUID.fromString(id);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario nao encontrado");
		}
		usuarioService.update(uuid, request);
		return ResponseEntity.ok().build();
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable String id) {
		UUID uuid;
		try {
			uuid = UUID.fromString(id);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario nao encontrado");
		}
		usuarioService.softDelete(uuid);
		return ResponseEntity.ok().build();
	}
}
