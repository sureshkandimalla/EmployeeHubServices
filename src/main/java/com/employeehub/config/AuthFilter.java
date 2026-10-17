package com.employeehub.config;

import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.employeehub.repository.UserRoleRepository;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

// Runs on every /api/** request (see WebConfig for the exact registration/
// exclusions). Verifies the app JWT issued at login (see AuthController),
// sets the resolved tenant into TenantContext for TenantRoutingDataSource
// to read, resolves the caller's role into CurrentUserContext (so
// controllers can gate sensitive actions server-side — see
// CurrentUserContext#requireRole), and rejects the request with 401 if the
// token is missing, invalid, or expired. Cleared in a finally so ThreadLocal
// state never leaks onto the thread's next, unrelated request (containers
// reuse request-handling threads).
@Component
public class AuthFilter extends OncePerRequestFilter {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRoleRepository userRoleRepository;

    // Only the app's own /api/v1/** business endpoints require the token —
    // the login endpoint itself (which issues it), actuator health checks
    // (used by Elastic Beanstalk), and Swagger/OpenAPI docs stay open.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/") || path.equals("/api/v1/auth/login");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // CORS preflight requests carry no Authorization header — must pass
        // through untouched or the browser never gets to send the real request.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
        Claims claims = token != null ? jwtService.verifyToken(token) : null;

        if (claims == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Not authenticated\"}");
            return;
        }

        try {
            TenantContext.set((String) claims.get("tenant"));
            // Role lookup needs TenantContext already set (user_role is a
            // per-tenant table, same ordering AuthController's login uses).
            String role = userRoleRepository.findByEmailIgnoreCase(claims.getSubject())
                    .map(com.employeehub.model.UserRole::getRole)
                    .orElse(null);
            CurrentUserContext.set(claims.getSubject(), role);
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            CurrentUserContext.clear();
        }
    }
}
