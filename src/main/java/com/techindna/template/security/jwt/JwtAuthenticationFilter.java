package com.techindna.template.security.jwt;

import static java.util.List.of;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import jakarta.annotation.Nonnull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(
            @Nonnull HttpServletRequest request,
            @Nonnull HttpServletResponse response,
            @Nonnull FilterChain filterChain)
            throws ServletException, IOException {
        String token = extractTokenFromHeader(request);

        if (token != null) {
            try {
                Claims claims = tokenProvider.validateToken(token);
                String userId = claims.getSubject();
                String role = claims.get("role", String.class);
                String ipAddress = claims.get("ip_address", String.class);

                List<SimpleGrantedAuthority> authorities =
                        role != null && !role.isBlank()
                                ? of(new SimpleGrantedAuthority("ROLE_" + role))
                                : of();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);
                authentication.setDetails(ipAddress != null ? ipAddress : "");

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException ignored) {}
        }

        filterChain.doFilter(request, response);
    }

    private String extractTokenFromHeader(HttpServletRequest request) {
        String header = request.getHeader("Authorization");

        return (header != null && header.startsWith("Bearer "))
                ? header.substring(7) : null;
    }
}
