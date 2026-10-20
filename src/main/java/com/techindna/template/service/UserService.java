package com.techindna.template.service;

import com.techindna.template.dto.PageResponse;
import com.techindna.template.dto.UpdateUserRequest;
import com.techindna.template.dto.UserResponse;
import com.techindna.template.entity.enums.UserRole;
import com.techindna.template.exception.http.ConflictException;
import com.techindna.template.exception.http.ForbiddenException;
import com.techindna.template.exception.http.NotFoundException;
import com.techindna.template.repository.UserRepository;
import com.techindna.template.repository.model.JUser;
import com.techindna.template.security.RBACRules;
import com.techindna.template.service.event.AccountDeletionNotificationService;
import com.techindna.template.service.mapper.UserMapper;
import com.techindna.template.validator.DataValidator;
import com.techindna.template.validator.UserValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final UserValidator userValidator;
    private final AccountDeletionNotificationService accountDeletionNotificationService;

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId, Authentication authentication) {
        JUser user = findUser(userId, () -> new NotFoundException("User not found"));

        RBACRules.authorize(
                RBACRules.roleOf(authentication),
                UUID.fromString(authentication.getName()),
                user.getRole(),
                user.getId());

        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateUser(
            UUID userId, UpdateUserRequest request, Authentication authentication) {
        userValidator.validateUpdate(request);

        JUser user =
                findUser(userId, () -> new ForbiddenException("Cannot access this resource."));

        RBACRules.requireOwner(UUID.fromString(authentication.getName()), user.getId());

        String username = dataValidator.normalizeUsername(request.username());
        user.setUsername(username == null ? user.getUsername() : username);
        String firstName = dataValidator.normalizeName(request.firstName());
        String lastName = dataValidator.normalizeName(request.lastName());
        user.setFirstName(firstName == null ? user.getFirstName() : firstName);
        user.setLastName(lastName == null ? user.getLastName() : lastName);

        try {
            return userMapper.toResponse(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Cannot use this username");
        }
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

    @Transactional
    public void deleteUser(
            UUID userId, Authentication authentication, HttpServletRequest servletRequest) {
        JUser user = findUser(userId, () -> new NotFoundException("User not found"));
        UUID requesterId = UUID.fromString(authentication.getName());

        RBACRules.authorize(
                RBACRules.roleOf(authentication), requesterId, user.getRole(), user.getId());

        userRepository.delete(user);
        accountDeletionNotificationService.sendAccountDeletionNotification(
                user, servletRequest, requesterId.equals(user.getId()));
    }

    private JUser findUser(UUID userId, Supplier<? extends RuntimeException> onMissing) {
        return userRepository.findById(userId).orElseThrow(onMissing);
    }
}