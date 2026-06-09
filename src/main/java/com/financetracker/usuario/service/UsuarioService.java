package com.financetracker.usuario.service;

import com.financetracker.security.TokenService;
import com.financetracker.usuario.dto.LoginRequest;
import com.financetracker.usuario.dto.LoginResponse;
import com.financetracker.usuario.dto.UsuarioRegisterRequest;
import com.financetracker.usuario.entity.Usuario;
import com.financetracker.usuario.repository.UsuarioRepository;
import com.financetracker.usuario.util.PasswordUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UsuarioService {

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;
	private final TokenService tokenService;

	public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, TokenService tokenService) {
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
	}

	public LoginResponse login(LoginRequest request) {
		if (request == null || isBlank(request.email()) || isBlank(request.password())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Required fields missing");
		}

		if (!isValidEmail(request.email())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email");
		}

		Usuario usuario = usuarioRepository.findByEmail(request.email())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

		if (!passwordEncoder.matches(request.password(), usuario.getSenha())) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
		}

		String token = tokenService.generateToken(usuario.getEmail());
		return new LoginResponse(token);
	}

	public void register(UsuarioRegisterRequest request) {
		validateRequest(request);

		if (usuarioRepository.existsByEmail(request.email())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
		}

		String nome = deriveNomeFromEmail(request.email());
		String encodedPassword = passwordEncoder.encode(request.password());
		Usuario usuario = new Usuario(nome, request.email(), encodedPassword);
		usuarioRepository.save(usuario);
	}

	private void validateRequest(UsuarioRegisterRequest request) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payload");
		}

		if (isBlank(request.email()) || isBlank(request.password()) || isBlank(request.confirmPassword())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Required fields missing");
		}

		if (!request.password().equals(request.confirmPassword())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match");
		}

		if (!isValidEmail(request.email())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email");
		}

		if (!PasswordUtils.isStrongPassword(request.password())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Weak password");
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}

	private boolean isValidEmail(String email) {
		return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
	}

	private String deriveNomeFromEmail(String email) {
		int atIndex = email.indexOf('@');
		if (atIndex <= 0) {
			return "Usuario";
		}
		return email.substring(0, atIndex);
	}
}
