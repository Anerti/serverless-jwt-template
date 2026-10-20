package com.techindna.template.validator;

import com.techindna.template.exception.http.BadRequestException;
import com.techindna.template.exception.http.UnprocessableContentException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class DataValidator {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;

    private static final Pattern EMAIL_FORMAT = Pattern.compile(
            "^[a-z0-9_-]+(\\.[a-z0-9_-]+)*@([a-z0-9]([a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,}$");
    private static final Pattern NAME_FORMAT = Pattern.compile("^[\\p{L}\\p{M}]+([ '’-][\\p{L}\\p{M}]+)*$");
    private static final Pattern USERNAME_FORMAT = Pattern.compile("^[a-zA-Z_0-9-]{2,}$");
    private static final Pattern SEARCH_FORMAT = Pattern.compile("^[0-9a-zA-Z.@_\\- éèê]*$");

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

    public void validateSearchString(String value) {
        checkStringLength("search", value, 100);
        if (value != null && !SEARCH_FORMAT.matcher(value).matches()) {
            throw new UnprocessableContentException(
                    "Search may only contain letters, digits, dot, @, underscore, hyphen, space, and éèê");
        }
    }

    public void validatePagination(int page, int size) {
        if (page < 1 || size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Invalid pagination parameters");
        }
    }

    public String normalizeUsername(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    public String normalizeEmail(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    public String normalizeName(String value) {
        return value == null ? null : value.trim();
    }

    public void validateUsername(String value){
        if (value != null && !USERNAME_FORMAT.matcher(normalizeUsername(value)).matches()){
            throw new UnprocessableContentException(String.format("Username %s is invalid", value));
        }
    }

    public void validateEmail(String value) {
        if (value != null && !EMAIL_FORMAT.matcher(normalizeEmail(value)).matches()) {
            throw new UnprocessableContentException(String.format("Email %s is not valid", value));
        }
    }

    public void validateName(String field, String value) {
        if (value != null && !NAME_FORMAT.matcher(normalizeName(value)).matches()) {
            throw new UnprocessableContentException(String.format("%s must contain only letters, with single spaces, apostrophes, or hyphens between words", field));
        }
    }

    public void checkPasswordSecurityLevel(String password) {
        checkPasswordSecurityLevel("password", password, "Password");
    }

    public void checkPasswordSecurityLevel(String field, String password, String label) {
        checkNullData(field, password);
        checkStringLength(field, password, 255);

        if (password.length() < 12) {
            throw new UnprocessableContentException(label + " must be at least 12 characters");
        }

        if (!password.matches(".*[A-Z].*")) {
            throw new UnprocessableContentException(label + " must contain at least one uppercase character");
        }

        if (!password.matches(".*[a-z].*")) {
            throw new UnprocessableContentException(label + " must contain at least one lowercase character");
        }

        if (!password.matches(".*[0-9].*")) {
            throw new UnprocessableContentException(label + " must contain at least one digit");
        }

        if (!password.matches(".*[!?*+=@#$%^&()_\\-\\[\\]{}|\\\\:;\"'<>,./`~].*")) {
            throw new UnprocessableContentException(label + " must contain at least one special character");
        }
    }
}
