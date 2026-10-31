package com.autoflow.infrastructure.security;

import com.autoflow.application.policy.ClienteAtivoPolicy;
import com.autoflow.infrastructure.security.service.CustomUserDetailsService;
import com.autoflow.infrastructure.security.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final ClienteAtivoPolicy clienteAtivoPolicy;

    @Override
    public void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        if (SecurityContextHolder.getContext().getAuthentication() == null
                && jwtService.tokenValido(token)) {
            String cpfCnpj = jwtService.extrairCpfCnpj(token);

            if (cpfCnpj != null) {
                UserDetails userDetails;
                try {
                    userDetails = userDetailsService.loadUserByCpfCnpj(cpfCnpj);
                } catch (UsernameNotFoundException exception) {
                    filterChain.doFilter(request, response);
                    return;
                }

                boolean usuarioCliente = userDetails.getAuthorities().stream()
                        .anyMatch(authority -> "ROLE_CLIENTE".equals(authority.getAuthority()));
                if (!clienteAtivoPolicy.podeAutenticar(cpfCnpj, usuarioCliente)
                        || !userDetails.isEnabled()) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                authToken.setDetails(
                        new WebAuthenticationDetailsSource()
                                .buildDetails(request)
                );

                SecurityContextHolder.getContext()
                        .setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }

    @Override
    public boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || path.startsWith("/public/")
                || path.equals("/auth/cadastro")
                || path.equals("/auth/login")
                || path.equals("/actuator/health")
                || path.equals("/actuator/health/liveness")
                || path.equals("/actuator/health/readiness")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/swagger-ui.html")
                || path.startsWith("/public/orcamentos")
                || path.equals("/swagger-ui.html");
    }
}
