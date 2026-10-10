package com.techindna.template.service;

import com.techindna.template.dto.UserResponse;
import com.techindna.template.exception.http.NotFoundException;
import com.techindna.template.repository.UserRepository;
import com.techindna.template.repository.model.JUser;
import com.techindna.template.security.RBACRules;
import com.techindna.template.service.mapper.UserMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public UserResponse getUserById(
            UUID userId, UUID requesterId, Authentication authentication) {
        JUser user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new NotFoundException("User not found"));

        RBACRules.authorize(
                RBACRules.roleOf(authentication), requesterId, user.getRole(), user.getId());

        return userMapper.toResponse(user);
    }
}
