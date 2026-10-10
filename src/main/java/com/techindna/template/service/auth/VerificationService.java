package com.techindna.template.service.auth;

import com.techindna.template.dto.auth.VerificationResponse;
import com.techindna.template.entity.enums.UserStatus;
import com.techindna.template.exception.http.ForbiddenException;
import com.techindna.template.exception.http.UnauthorizedException;
import com.techindna.template.repository.UserRepository;
import com.techindna.template.repository.model.JUser;
import com.techindna.template.security.ClientIpAddressResolver;
import com.techindna.template.security.jwt.JwtTokenProvider;
import com.techindna.template.service.mapper.UserMapper;
import com.techindna.template.service.enums.VerificationFlow;
import com.techindna.template.service.redis.VerificationTokenService;
import com.techindna.template.service.redis.VerificationTokenService.VerificationToken;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VerificationService {

    private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired token";
    private static final String LOCKED_ACCOUNT_MESSAGE =
            "Account is locked or inactive. Please request account access restoration to continue.";

    private final VerificationTokenService verificationTokenService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final ClientIpAddressResolver clientIpAddressResolver;

    @Transactional
    public VerificationResponse confirmRegistration(String token, HttpServletRequest request) {
        JUser user = resolveUser(token, VerificationFlow.REGISTER);

        if (Boolean.FALSE.equals(user.getVerified())) {
            user.setVerified(true);
            user = userRepository.save(user);
        }

        return issueToken(user, request);
    }

    @Transactional
    public VerificationResponse confirmLogin(String token, HttpServletRequest request) {
        JUser user = resolveUser(token, VerificationFlow.LOGIN);

        if (!Boolean.TRUE.equals(user.getVerified())) {
            throw new ForbiddenException("Verify your email address before signing in.");
        }

        return issueToken(user, request);
    }

    private JUser resolveUser(String token, VerificationFlow expectedFlow) {
        VerificationToken verificationToken = verificationTokenService.consume(token);
        if (verificationToken == null || verificationToken.flow() != expectedFlow) {
            throw new UnauthorizedException(INVALID_TOKEN_MESSAGE);
        }

        JUser user =
                userRepository
                        .findById(verificationToken.userId())
                        .orElseThrow(() -> new UnauthorizedException(INVALID_TOKEN_MESSAGE));

        if (user.getStatus() == UserStatus.LOCKED || user.getStatus() == UserStatus.INACTIVE) {
            throw new ForbiddenException(LOCKED_ACCOUNT_MESSAGE);
        }

        return user;
    }

    private VerificationResponse issueToken(JUser user, HttpServletRequest request) {
        return new VerificationResponse(
                jwtTokenProvider.generateToken(
                        user.getId().toString(),
                        user.getRole().name(),
                        clientIpAddressResolver.resolve(request)),
                userMapper.toResponse(user));
    }
}
