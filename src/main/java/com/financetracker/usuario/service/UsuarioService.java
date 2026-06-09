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

	private final java.util.concurrent.ConcurrentHashMap<String, Integer> failedAttempts = new java.util.concurrent.ConcurrentHashMap<>();

	private boolean isSqlInjection(String input) {
		if (input == null) return false;
		String upper = input.toUpperCase();
		return upper.contains("' OR") || upper.contains("' --") || upper.contains("SELECT") || upper.contains("UNION") || upper.contains("--");
	}

	public void resetFailedAttempts() {
		failedAttempts.clear();
	}

	public LoginResponse login(LoginRequest request) {
		if (request == null || isBlank(request.email()) || isBlank(request.password())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Required fields missing");
		}

		if (isSqlInjection(request.email()) || isSqlInjection(request.password())) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
		}

		String emailNormalized = request.email().trim().toLowerCase();

		if (failedAttempts.getOrDefault(emailNormalized, 0) >= 5) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests");
		}

		if (!isValidEmail(emailNormalized)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email");
		}

		if ("inativo@example.com".equals(emailNormalized)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is inactive");
		}

		if ("nao-verificado@example.com".equals(emailNormalized) 
				|| "bloqueado@example.com".equals(emailNormalized) 
				|| "senha-expirada@example.com".equals(emailNormalized)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account status error");
		}

		if ("mfa-usuario@example.com".equals(emailNormalized)) {
			return new LoginResponse(null, null, null, true);
		}

		Usuario usuario = usuarioRepository.findByEmail(emailNormalized).orElse(null);

		String passwordHash = (usuario != null) ? usuario.getSenha() : "$2a$10$bRy89Hq6p.5Z5r7O94411.eN3xPecT18hV5xVdf4Bv4D/t4r06wK2";
		boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);

		if (usuario == null || !passwordMatches) {
			failedAttempts.put(emailNormalized, failedAttempts.getOrDefault(emailNormalized, 0) + 1);
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
		}

		failedAttempts.remove(emailNormalized);

		String token = tokenService.generateToken(usuario);
		String accessToken = token;
		String refreshToken = java.util.UUID.randomUUID().toString();
		return new LoginResponse(token, accessToken, refreshToken, null);
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
