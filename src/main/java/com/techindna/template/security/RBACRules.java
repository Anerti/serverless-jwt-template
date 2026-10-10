package com.techindna.template.security;

import com.techindna.template.entity.enums.UserRole;
import com.techindna.template.exception.http.ForbiddenException;
import java.util.UUID;

public final class RBACRules {

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
