package com.techindna.template.service.mapper;

import com.techindna.template.dto.Meta;
import com.techindna.template.dto.PageResponse;
import com.techindna.template.dto.UserResponse;
import com.techindna.template.dto.auth.RegisterRequest;
import com.techindna.template.repository.model.JUser;
import com.techindna.template.validator.DataValidator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
                .firstName(dataValidator.normalizeName(request.firstName()))
                .lastName(dataValidator.normalizeName(request.lastName()))
                .email(dataValidator.normalizeEmail(request.email()))
                .verified(false)
                .build();
    }

    public UserResponse toResponse(JUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole().name().toLowerCase(Locale.ROOT),
                user.getStatus().name().toLowerCase(Locale.ROOT),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    public PageResponse<UserResponse> toPageResponse(Page<JUser> users, int page, int size) {
        List<UserResponse> data = users.getContent().stream().map(this::toResponse).toList();
        return new PageResponse<>(data, new Meta(page, size, users.getTotalElements()));
    }
}
