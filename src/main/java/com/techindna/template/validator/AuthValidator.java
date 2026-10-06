package com.techindna.template.validator;

import com.techindna.template.dto.auth.RegisterRequest;
import com.techindna.template.exception.http.UnprocessableContentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthValidator {

    private final DataValidator dataValidator;

    public void validateRegistration(RegisterRequest request) {
        dataValidator.validateUsername(request.username());
        dataValidator.checkPasswordSecurityLevel(request.password());
        dataValidator.checkNullData("confirmPassword", request.confirmPassword());

        if (!request.password().equals(request.confirmPassword())) {
            throw new UnprocessableContentException("Password and confirmPassword must match");
        }
        dataValidator.validateName("firstName", request.firstName());
        dataValidator.validateName("lastName", request.lastName());
        dataValidator.validateEmail("email", request.email());
    }
}
