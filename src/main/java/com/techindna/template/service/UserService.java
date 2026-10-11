package com.techindna.template.service;

import com.techindna.template.dto.PageResponse;
import com.techindna.template.dto.UserResponse;
import com.techindna.template.entity.enums.UserRole;
import com.techindna.template.exception.http.NotFoundException;
import com.techindna.template.repository.UserRepository;
import com.techindna.template.repository.model.JUser;
import com.techindna.template.security.RBACRules;
import com.techindna.template.service.mapper.UserMapper;
import com.techindna.template.validator.DataValidator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final DataValidator dataValidator;

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

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> findAll(
            String search, String order, String role, int page, int size) {
        dataValidator.validateSearchString(search);
        dataValidator.validatePagination(page, size);

        PageRequest pageable =
                PageRequest.of(
                        page - 1,
                        size,
                        Sort.by(
                                "desc".equalsIgnoreCase(order)
                                        ? Sort.Direction.DESC
                                        : Sort.Direction.ASC,
                                "createdAt"));

        return userMapper.toPageResponse(
                userRepository.findByRoleAndSearch(
                        "admin".equalsIgnoreCase(role) ? UserRole.ADMIN : UserRole.CUSTOMER,
                        (search == null || search.isBlank()) ? null : search,
                        pageable),
                page,
                size);
    }
}