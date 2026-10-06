package com.techindna.template.service.mapper;

import com.techindna.template.dto.auth.RegisterRequest;
import com.techindna.template.repository.model.JUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserMapper {

    private final PasswordEncoder passwordEncoder;

    public JUser toPersistenceModel(RegisterRequest request) {
        return JUser.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .verified(false)
                .build();
    }
}
