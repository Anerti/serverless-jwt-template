package com.techindna.template.service.auth;

import com.techindna.template.dto.auth.VerificationResponse;
import com.techindna.template.exception.http.UnauthorizedException;
import com.techindna.template.repository.UserRepository;
import com.techindna.template.repository.model.JUser;
import com.techindna.template.security.jwt.ClientIpAddressResolver;
import com.techindna.template.security.jwt.JwtTokenProvider;
import com.techindna.template.service.mapper.UserMapper;
import com.techindna.template.service.redis.VerificationTokenService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VerificationService {

    private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired token";

    private final VerificationTokenService verificationTokenService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final ClientIpAddressResolver clientIpAddressResolver;

    @Transactional
    public VerificationResponse verify(String token, HttpServletRequest request) {
        String userId = verificationTokenService.consume(token);
        if (userId == null) {
            throw new UnauthorizedException(INVALID_TOKEN_MESSAGE);
        }

        UUID id;
        try {
            id = UUID.fromString(userId);
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedException(INVALID_TOKEN_MESSAGE);
        }

        JUser user =
                userRepository
                        .findById(id)
                        .orElseThrow(() -> new UnauthorizedException(INVALID_TOKEN_MESSAGE));

        if (Boolean.FALSE.equals(user.getVerified())) {
            user.setVerified(true);
            user = userRepository.save(user);
        }

        String jwt =
                jwtTokenProvider.generateToken(
                        user.getId().toString(),
                        user.getRole().name(),
                        clientIpAddressResolver.resolve(request));
        return new VerificationResponse(jwt, userMapper.toResponse(user));
    }
}
