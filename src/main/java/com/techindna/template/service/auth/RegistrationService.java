package com.techindna.template.service.auth;

import com.techindna.template.dto.MessageResponse;
import com.techindna.template.dto.auth.RegisterRequest;
import com.techindna.template.exception.http.ConflictException;
import com.techindna.template.repository.UserRepository;
import com.techindna.template.repository.model.JUser;
import com.techindna.template.service.event.auth.AuthVerificationEmailService;
import com.techindna.template.service.mapper.UserMapper;
import com.techindna.template.validator.AuthValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AuthVerificationEmailService verificationEmailService;
    private final AuthValidator authValidator;

    @Transactional
    public MessageResponse register(RegisterRequest request, HttpServletRequest servletRequest) {
        authValidator.validateRegistration(request);

        JUser user = userMapper.toPersistenceModel(request);

        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            String constraint = e.getMostSpecificCause().getMessage();
            if (constraint != null && constraint.contains("email")) {
                throw new ConflictException("You cannot use this email address");
            }
            if (constraint != null && constraint.contains("username")) {
                throw new ConflictException("You cannot use this username");
            }
            throw e;
        }

        verificationEmailService.sendRegistrationVerification(user, servletRequest);

        return new MessageResponse("An email has been sent to verify your account");
    }
}
