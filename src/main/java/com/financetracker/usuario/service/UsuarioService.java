package com.financetracker.usuario.service;

import com.financetracker.security.TokenService;
import com.financetracker.usuario.dto.ExtendSessionRequest;
import com.financetracker.usuario.dto.LoginRequest;
import com.financetracker.usuario.dto.LoginResponse;
import com.financetracker.usuario.dto.UsuarioRegisterRequest;
import com.financetracker.usuario.dto.UsuarioResponse;
import com.financetracker.usuario.dto.UsuarioUpdateRequest;
import com.financetracker.usuario.entity.Usuario;
import com.financetracker.usuario.repository.UsuarioRepository;
import com.financetracker.usuario.util.PasswordUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

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
		if (isSqlInjection(request.email()) || isSqlInjection(request.password())) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais invalidas");
		}

		String emailNormalized = request.email().trim().toLowerCase();

		if (failedAttempts.getOrDefault(emailNormalized, 0) >= 5) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas de login");
		}

		Usuario usuario = usuarioRepository.findByEmail(emailNormalized).orElse(null);

		String passwordHash = (usuario != null) ? usuario.getSenha() : "$2a$10$bRy89Hq6p.5Z5r7O94411.eN3xPecT18hV5xVdf4Bv4D/t4r06wK2";
		boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);

		if (usuario == null || !passwordMatches) {
			failedAttempts.put(emailNormalized, failedAttempts.getOrDefault(emailNormalized, 0) + 1);
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais invalidas");
		}

		if (!usuario.isAtivo()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conta inativa");
		}

		if (!usuario.isVerificado()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conta nao verificada");
		}

		if (usuario.isBloqueado()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conta bloqueada");
		}

		if (usuario.isSenhaExpirada()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Senha expirada");
		}

		if (usuario.isMfaHabilitado()) {
			return new LoginResponse(null, null, null, true);
		}

		failedAttempts.remove(emailNormalized);

		String token = tokenService.generateToken(usuario);
		String accessToken = token;
		String refreshToken = java.util.UUID.randomUUID().toString();
		return new LoginResponse(token, accessToken, refreshToken, null);
	}

	public void register(UsuarioRegisterRequest request) {
		validateRequest(request);

		String emailNormalizado = request.email().trim().toLowerCase();

		if (usuarioRepository.existsByEmail(emailNormalizado)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail ja cadastrado");
		}

		if (usuarioRepository.existsByNome(request.name())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Nome ja cadastrado");
		}

		String nome = request.name();
		String encodedPassword = passwordEncoder.encode(request.password());
		Usuario usuario = new Usuario(nome, emailNormalizado, encodedPassword);
		usuarioRepository.save(usuario);
	}

	private void validateRequest(UsuarioRegisterRequest request) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados invalidos");
		}

		if (!request.password().equals(request.confirmPassword())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "As senhas nao coincidem");
		}

		if (!PasswordUtils.isStrongPassword(request.password())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Senha fraca");
		}
	}

	/**
	 * Retorna o usuário autenticado a partir do SecurityContext.
	 * Lança 401 se não houver autenticação válida.
	 */
	private Usuario getAuthenticatedUsuario() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nao autenticado");
		}
		String email = authentication.getName();
		return usuarioRepository.findByEmail(email)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nao autenticado"));
	}

	/**
	 * Retorna os dados do próprio usuário autenticado.
	 */
	public UsuarioResponse getMe() {
		Usuario usuario = getAuthenticatedUsuario();
		return new UsuarioResponse(usuario);
	}

	/**
	 * Retorna os dados de um usuário pelo ID.
	 * Apenas o próprio usuário pode consultar seus dados.
	 */
	public UsuarioResponse getById(UUID id) {
		Usuario autenticado = getAuthenticatedUsuario();
		if (!autenticado.getId().equals(id)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado");
		}
		if (!autenticado.isAtivo()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario inativo");
		}
		return new UsuarioResponse(autenticado);
	}

	/**
	 * Atualiza nome e e-mail do próprio usuário autenticado.
	 * Valida ownership, normaliza e-mail e garante unicidade.
	 */
	public void update(UUID id, UsuarioUpdateRequest request) {
		Usuario autenticado = getAuthenticatedUsuario();
		if (!autenticado.getId().equals(id)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado");
		}
		if (!autenticado.isAtivo()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario inativo");
		}

		String emailNormalizado = request.email().trim().toLowerCase();

		// Verifica unicidade apenas se o e-mail for diferente do atual
		if (!autenticado.getEmail().equals(emailNormalizado) && usuarioRepository.existsByEmail(emailNormalizado)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail ja cadastrado");
		}

		autenticado.setNome(request.name());
		autenticado.setEmail(emailNormalizado);
		usuarioRepository.save(autenticado);
	}

	/**
	 * Valida a senha do usuário autenticado para retornar um novo token estendido.
	 */
	public LoginResponse extendSession(ExtendSessionRequest request) {
		Usuario autenticado = getAuthenticatedUsuario();
		String emailNormalized = autenticado.getEmail().trim().toLowerCase();

		if (failedAttempts.getOrDefault(emailNormalized, 0) >= 5) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas de login");
		}

		if (!passwordEncoder.matches(request.password(), autenticado.getSenha())) {
			failedAttempts.put(emailNormalized, failedAttempts.getOrDefault(emailNormalized, 0) + 1);
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Senha incorreta");
		}

		failedAttempts.remove(emailNormalized);

		String token = tokenService.generateToken(autenticado);
		String accessToken = token;
		String refreshToken = java.util.UUID.randomUUID().toString();
		return new LoginResponse(token, accessToken, refreshToken, null);
	}

	/**
	 * Desativa (soft delete) a conta do próprio usuário autenticado.
	 * Valida ownership antes de executar.
	 */
	public void softDelete(UUID id) {
		Usuario autenticado = getAuthenticatedUsuario();
		if (!autenticado.getId().equals(id)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado");
		}
		if (!autenticado.isAtivo()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario ja deletado");
		}
		autenticado.setAtivo(false);
		usuarioRepository.save(autenticado);
	}
}
