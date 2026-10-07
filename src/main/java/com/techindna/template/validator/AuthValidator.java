package com.techindna.template.validator;

import com.techindna.template.dto.auth.LoginRequest;
import com.techindna.template.dto.auth.RegisterRequest;
import com.techindna.template.exception.http.UnprocessableContentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class AuthValidator {

    private final DataValidator dataValidator;

    public void validateRegistration(RegisterRequest request) {
        dataValidator.checkNullData("username", request.username());
        dataValidator.checkStringLength("username", request.username(), 50);
        dataValidator.validateUsername(request.username());

        dataValidator.checkPasswordSecurityLevel(request.password());
        dataValidator.checkNullData("confirmPassword", request.confirmPassword());

        if (!request.password().equals(request.confirmPassword())) {
            throw new UnprocessableContentException("Password and confirmPassword must match");
        }
        dataValidator.checkNullData("firstName", request.firstName());
        dataValidator.checkStringLength("firstName", request.firstName(), 100);
        dataValidator.validateName("firstName", request.firstName());

        dataValidator.checkNullData("lastName", request.lastName());
        dataValidator.checkStringLength("lastName", request.lastName(), 100);
        dataValidator.validateName("lastName", request.lastName());

        dataValidator.checkNullData("email", request.email());
        dataValidator.checkStringLength("email", request.email(), 100);
        dataValidator.validateEmail(request.email());
    }

    public void validateLogin(LoginRequest request) {
        boolean hasUsername = StringUtils.hasText(request.username());
        boolean hasEmail = StringUtils.hasText(request.email());
        if (!hasUsername && !hasEmail) {
            throw new UnprocessableContentException(
                    "Username or email is required and cannot be blank");
        }

        if (hasUsername) {
            dataValidator.checkStringLength("username", request.username(), 50);
            dataValidator.validateUsername(request.username());
        }
        if (hasEmail) {
            dataValidator.checkStringLength("email", request.email(), 100);
            dataValidator.validateEmail(request.email());
        }
        dataValidator.checkNullData("password", request.password());
    }
}
