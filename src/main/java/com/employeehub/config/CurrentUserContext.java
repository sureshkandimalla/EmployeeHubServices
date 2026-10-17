package com.employeehub.config;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

// Holds the current request's authenticated email and role (ADMIN | HR |
// ACCOUNTING | IMMIGRATION | null), set by AuthFilter once it has verified
// the JWT and resolved the role from UserRoleRepository — same ThreadLocal
// pattern as TenantContext, set up and torn down alongside it on every
// request. A role of null means "no row in user_role yet", i.e. no access
// granted — requireRole() rejects that the same as any other mismatch.
public final class CurrentUserContext {

    private static final ThreadLocal<String> EMAIL = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void set(String email, String role) {
        EMAIL.set(email);
        ROLE.set(role);
    }

    public static String email() {
        return EMAIL.get();
    }

    public static String role() {
        return ROLE.get();
    }

    public static void clear() {
        EMAIL.remove();
        ROLE.remove();
    }

    // Call as the first line of any controller method that must be
    // restricted to specific roles — throws 403 if the current user's role
    // (set by AuthFilter from their JWT's email) isn't one of allowedRoles.
    public static void requireRole(String... allowedRoles) {
        String role = ROLE.get();
        if (role == null || Arrays.stream(allowedRoles).noneMatch(role::equals)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This action requires one of: " + Arrays.toString(allowedRoles));
        }
    }
}
