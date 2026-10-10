package com.techindna.template.security;

import com.techindna.template.exception.http.ForbiddenException;

public final class ABACRules {

    public static void authorizeIpAddress(String tokenIpAddress, String requestIpAddress) {
        if (tokenIpAddress == null
                || tokenIpAddress.isBlank()
                || (requestIpAddress != null && !tokenIpAddress.equals(requestIpAddress))) {
            throw new ForbiddenException("Cannot access this resource from the current IP address.");
        }
    }
}
