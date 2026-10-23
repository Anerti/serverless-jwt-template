package com.techindna.template.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientIpAddressResolver {

    public String resolve(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
