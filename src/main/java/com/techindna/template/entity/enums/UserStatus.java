package com.techindna.template.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** Account states accepted by the {@code template_app.user_status} enum and the API wire format. */
public enum UserStatus {

    ACTIVE("active"),
    INACTIVE("inactive"),
    LOCKED("locked");

    private final String value;

    UserStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static UserStatus fromValue(String value) {
        return Arrays.stream(values())
                .filter(status -> status.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown user status: " + value));
    }
}