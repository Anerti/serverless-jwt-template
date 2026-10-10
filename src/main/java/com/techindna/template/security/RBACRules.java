package com.techindna.template.security;

import com.techindna.template.entity.enums.UserRole;
import com.techindna.template.exception.http.ForbiddenException;

import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public final class RBACRules {

    private static final String ROLE_PREFIX = "ROLE_";

    public static UserRole roleOf(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).filter(Objects::nonNull)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .map(UserRole::valueOf)
                .findFirst()
                .orElseThrow(
                        () ->
                                new ForbiddenException(
                                        "Insufficient privileges to access this resource"));
    }

    public static boolean canAccess(
            UserRole requesterRole, UUID requesterId, UserRole ownerRole, UUID ownerId) {
        return requesterRole == UserRole.ADMIN
                ? ownerRole != UserRole.ADMIN || requesterId.equals(ownerId)
                : requesterRole == UserRole.CUSTOMER && requesterId.equals(ownerId);
    }

    public static void authorize(
            UserRole requesterRole, UUID requesterId, UserRole ownerRole, UUID ownerId) {
        if (!canAccess(requesterRole, requesterId, ownerRole, ownerId)) {
            throw new ForbiddenException("Cannot access this resource.");
        }
    }

    public static void requireOwner(UUID requesterId, UUID ownerId) {
        if (requesterId == null || !requesterId.equals(ownerId)) {
            throw new ForbiddenException("Cannot access this resource.");
        }
    }
}
