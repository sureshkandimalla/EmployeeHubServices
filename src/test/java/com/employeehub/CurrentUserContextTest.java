package com.employeehub;

import com.employeehub.config.CurrentUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CurrentUserContext is the single gate every role-restricted controller
 * method calls — getting it wrong silently opens or closes every protected
 * endpoint at once, so it is tested directly rather than only through
 * controllers.
 */
class CurrentUserContextTest {

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    @DisplayName("set/email/role: exposes the values the auth filter stored for this request")
    void set_exposesEmailAndRole() {
        CurrentUserContext.set("hr@example.com", "HR");

        assertEquals("hr@example.com", CurrentUserContext.email());
        assertEquals("HR", CurrentUserContext.role());
    }

    @Test
    @DisplayName("clear: removes both email and role so a reused thread can't inherit them")
    void clear_removesEmailAndRole() {
        CurrentUserContext.set("hr@example.com", "HR");

        CurrentUserContext.clear();

        assertNull(CurrentUserContext.email());
        assertNull(CurrentUserContext.role());
    }

    @Test
    @DisplayName("requireRole: allows the request when the current role is in the allowed list")
    void requireRole_allowsMatchingRole() {
        CurrentUserContext.set("admin@example.com", "ADMIN");

        assertDoesNotThrow(() -> CurrentUserContext.requireRole("ADMIN", "ACCOUNTING"));
    }

    @Test
    @DisplayName("requireRole: allows when the role matches a later entry in the allowed list")
    void requireRole_allowsNonFirstMatchingRole() {
        CurrentUserContext.set("acct@example.com", "ACCOUNTING");

        assertDoesNotThrow(() -> CurrentUserContext.requireRole("ADMIN", "ACCOUNTING"));
    }

    @Test
    @DisplayName("requireRole: throws 403 when the current role is not allowed")
    void requireRole_rejectsWrongRole() {
        CurrentUserContext.set("hr@example.com", "HR");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> CurrentUserContext.requireRole("ADMIN", "ACCOUNTING"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    @DisplayName("requireRole: throws 403 when no role has been set at all (no user_role row yet)")
    void requireRole_rejectsNullRole() {
        CurrentUserContext.set("nobody@example.com", null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> CurrentUserContext.requireRole("ADMIN"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    @DisplayName("requireRole: throws 403 when nothing was ever set on this thread")
    void requireRole_rejectsUnsetContext() {
        assertThrows(ResponseStatusException.class, () -> CurrentUserContext.requireRole("ADMIN"));
    }

    @Test
    @DisplayName("requireRole: throws 403 when the allowed list is empty (admin-only endpoints)")
    void requireRole_rejectsEveryRoleWhenAllowedListEmpty() {
        CurrentUserContext.set("admin@example.com", "ADMIN");

        assertThrows(ResponseStatusException.class, CurrentUserContext::requireRole);
    }

    @Test
    @DisplayName("requireRole: role matching is case-sensitive, so 'admin' is not 'ADMIN'")
    void requireRole_isCaseSensitive() {
        CurrentUserContext.set("admin@example.com", "admin");

        assertThrows(ResponseStatusException.class, () -> CurrentUserContext.requireRole("ADMIN"));
    }

    @Test
    @DisplayName("set: one thread's role never leaks into another thread")
    void set_isPerThread() throws Exception {
        CurrentUserContext.set("admin@example.com", "ADMIN");
        AtomicReference<String> otherThreadRole = new AtomicReference<>("not-run");

        Thread other = new Thread(() -> otherThreadRole.set(CurrentUserContext.role()));
        other.start();
        other.join();

        assertNull(otherThreadRole.get(), "role must not be visible from another request thread");
        assertEquals("ADMIN", CurrentUserContext.role());
    }
}
