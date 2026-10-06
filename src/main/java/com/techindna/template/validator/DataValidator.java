package com.techindna.template.validator;

import com.techindna.template.exception.http.UnprocessableContentException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class DataValidator {

    private static final Pattern EMAIL_FORMAT = Pattern.compile("^[a-z0-9_.-]+@[a-z0-9_-]+(\\.[a-z]+)+$");
    private static final Pattern NAME_FORMAT = Pattern.compile("^[A-Z][a-z-'éèê ]{2,}$");
    private static final Pattern USERNAME_FORMAT = Pattern.compile("^[a-zA-Z_0-9-]{2,}$");

    public void checkNullData(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new UnprocessableContentException(String.format("%s is required and cannot be blank", field));
        }
    }

    public void checkStringLength(String field, String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new UnprocessableContentException(String.format("%s must not exceed %s characters", field, maxLength));
        }
    }

    public String normalizeUsername(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    public String normalizeEmail(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    public void validateUsername(String value){
        if (value != null && !USERNAME_FORMAT.matcher(normalizeUsername(value)).matches()){
            throw new UnprocessableContentException(String.format("Username %s is invalid", value));
        }
    }

    public void validateEmail(String field, String value) {
        if (value != null && !EMAIL_FORMAT.matcher(normalizeEmail(value)).matches()) {
            throw new UnprocessableContentException(String.format("Email %s is not valid", value));
        }
    }

    public void validateName(String field, String value) {
        if (value != null && !NAME_FORMAT.matcher(value).matches()) {
            throw new UnprocessableContentException(String.format("%s must start with a capital letter and contain only letters, hyphens, apostrophes, and spaces", field));
        }
    }

    public void checkPasswordSecurityLevel(String password) {
        checkNullData("password", password);

        if (password.length() < 12) {
            throw new UnprocessableContentException("Password must be at least 12 characters");
        }

        if (!password.matches(".*[A-Z].*")) {
            throw new UnprocessableContentException("Password must contain at least one uppercase character");
        }

        if (!password.matches(".*[a-z].*")) {
            throw new UnprocessableContentException("Password must contain at least one lowercase character");
        }

        if (!password.matches(".*[0-9].*")) {
            throw new UnprocessableContentException("Password must contain at least one digit");
        }

        if (!password.matches(".*[!?*+=@#$%^&()_\\-\\[\\]{}|\\\\:;\"'<>,./`~].*")) {
            throw new UnprocessableContentException("Password must contain at least one special character");
        }
    }
}
