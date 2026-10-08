package com.techindna.template.security.jwt;

import static java.util.List.of;

import com.techindna.template.exception.ErrorBody;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final ClientIpAddressResolver clientIpAddressResolver;

    public JwtAuthenticationFilter(
            JwtTokenProvider tokenProvider, ClientIpAddressResolver clientIpAddressResolver) {
        this.tokenProvider = tokenProvider;
        this.clientIpAddressResolver = clientIpAddressResolver;
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
                String tokenIpAddress = claims.get("ip_address", String.class);
                String requestIpAddress = clientIpAddressResolver.resolve(request);

                if (tokenIpAddress == null || tokenIpAddress.isBlank()) {
                    SecurityContextHolder.clearContext();
                    ErrorBody.send(response, HttpStatus.UNAUTHORIZED, "Invalid token.");
                    return;
                }

                if (requestIpAddress != null && !tokenIpAddress.equals(requestIpAddress)) {
                    SecurityContextHolder.clearContext();
                    ErrorBody.send(response, HttpStatus.UNAUTHORIZED, "IP address mismatch.");
                    return;
                }

                List<SimpleGrantedAuthority> authorities =
                        role != null && !role.isBlank()
                                ? of(new SimpleGrantedAuthority("ROLE_" + role))
                                : of();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);
                authentication.setDetails(tokenIpAddress);

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException ignored) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractTokenFromHeader(HttpServletRequest request) {
        String header = request.getHeader("Authorization");

        return (header != null && header.startsWith("Bearer "))
                ? header.substring(7) : null;
    }
}
