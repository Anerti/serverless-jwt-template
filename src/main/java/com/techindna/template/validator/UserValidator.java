package com.techindna.template.validator;

import com.techindna.template.dto.UpdateUserRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserValidator {

    private final DataValidator dataValidator;

    public void validateUpdate(UpdateUserRequest request) {
        dataValidator.checkStringLength("username", request.username(), 50);
        dataValidator.validateUsername(request.username());

        dataValidator.checkStringLength("firstName", request.firstName(), 100);
        dataValidator.validateName("firstName", request.firstName());

        dataValidator.checkStringLength("lastName", request.lastName(), 100);
        dataValidator.validateName("lastName", request.lastName());
    }
}
