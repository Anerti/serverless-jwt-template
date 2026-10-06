package com.techindna.template.service.mapper;

import com.techindna.template.dto.auth.RegisterRequest;
import com.techindna.template.repository.model.JUser;
import com.techindna.template.validator.DataValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserMapper {

    private final PasswordEncoder passwordEncoder;
    private final DataValidator dataValidator;

    public JUser toPersistenceModel(RegisterRequest request) {
        return JUser.builder()
                .username(dataValidator.normalizeUsername(request.username()))
                .password(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(dataValidator.normalizeEmail(request.email()))
                .verified(false)
                .build();
    }
}
