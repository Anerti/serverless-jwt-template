package com.techindna.template.controller;

import com.techindna.template.dto.UserResponse;
import com.techindna.template.service.UserService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/{userId}")
    public UserResponse getUserById(@PathVariable UUID userId, Authentication authentication) {
        return userService.getUserById(
                userId, UUID.fromString(authentication.getName()), authentication);
    }
}
