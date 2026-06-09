package com.financetracker.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class RateLimitingFilter implements Filter {

    private static final int MAX_REQUESTS = 5;
    private static final long TIME_WINDOW_MS = 10 * 60 * 1000; // 10 minutos

    private final ConcurrentHashMap<String, Queue<Long>> ipRequestTimestamps = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse) {
            String path = httpRequest.getRequestURI();
            
            // Aplica rate limit apenas no cadastro de usuários
            if ("/usuarios/register".equals(path) && "POST".equalsIgnoreCase(httpRequest.getMethod())) {
                String ip = getClientIp(httpRequest);
                long currentTime = System.currentTimeMillis();
                
                Queue<Long> timestamps = ipRequestTimestamps.computeIfAbsent(ip, k -> new ConcurrentLinkedQueue<>());
                
                // Limpa timestamps antigos (fora da janela de tempo)
                while (!timestamps.isEmpty() && (currentTime - timestamps.peek() > TIME_WINDOW_MS)) {
                    timestamps.poll();
                }
                
                if (timestamps.size() >= MAX_REQUESTS) {
                    httpResponse.setStatus(429); // Too Many Requests
                    httpResponse.setContentType("application/json");
                    httpResponse.getWriter().write("{\"error\": \"Too many requests. Please try again later.\"}");
                    return;
                }
                
                timestamps.add(currentTime);
            }
        }
        
        chain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }

    // Método auxiliar para testes/limpeza de estado
    public void resetLimits() {
        ipRequestTimestamps.clear();
    }
}
