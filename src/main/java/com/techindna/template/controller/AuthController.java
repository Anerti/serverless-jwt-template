package com.techindna.template.controller;

import com.techindna.template.dto.MessageResponse;
import com.techindna.template.dto.auth.LoginRequest;
import com.techindna.template.dto.auth.RegisterRequest;
import com.techindna.template.dto.auth.VerificationResponse;
import com.techindna.template.service.auth.LoginService;
import com.techindna.template.service.auth.RegistrationService;
import com.techindna.template.service.auth.VerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final VerificationService verificationService;

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(
            @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(registrationService.register(request, servletRequest));
    }

    @PostMapping("/login")
    public ResponseEntity<MessageResponse> login(
            @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(loginService.login(request, servletRequest));
    }

    @GetMapping("/verify/{token}")
    public ModelAndView confirm(
            @PathVariable String token, HttpServletResponse servletResponse) {
        servletResponse.setHeader("Cache-Control", "no-store");
        servletResponse.setHeader("Referrer-Policy", "no-referrer");
        return new ModelAndView("auth/auth-redirection", Map.of("token", token));
    }

    @PostMapping("/verify/{token}")
    public VerificationResponse verify(
            @PathVariable String token, HttpServletRequest servletRequest) {
        return verificationService.verify(token, servletRequest);
    }
}
