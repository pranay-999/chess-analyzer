package com.chessanalyzer.web;

import com.chessanalyzer.user.AuthService;
import com.chessanalyzer.user.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Sign up, log in, play as guest, and "who am I?". Logout is handled by Spring
 * Security itself (POST /api/auth/logout, see SecurityConfig).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    // Where Spring Security looks for "who is logged in" on later requests: the HTTP session.
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    public record Credentials(String username, String password) {
    }

    public record Me(UserPrincipal user) {   // user = null when not logged in
    }

    /**
     * GET /api/auth/me — the current user (or null). Asking for the CsrfToken here
     * makes Spring send the XSRF-TOKEN cookie, so the React app has it before its first POST.
     * @AuthenticationPrincipal = the principal we stored at login (null for anonymous visitors).
     */
    @GetMapping("/me")
    public Me me(@AuthenticationPrincipal UserPrincipal me, CsrfToken csrfToken) {
        csrfToken.getToken();
        return new Me(me);
    }

    @PostMapping("/signup")
    public Me signup(@RequestBody Credentials body, HttpServletRequest request, HttpServletResponse response) {
        return startSession(auth.signup(body.username(), body.password()), request, response);
    }

    @PostMapping("/login")
    public Me login(@RequestBody Credentials body, HttpServletRequest request, HttpServletResponse response) {
        return startSession(auth.login(body.username(), body.password()), request, response);
    }

    @PostMapping("/guest")
    public Me guest(HttpServletRequest request, HttpServletResponse response) {
        return startSession(auth.createGuest(), request, response);
    }

    /** Marks this browser session as logged in as {@code user}. */
    private Me startSession(UserPrincipal user, HttpServletRequest request, HttpServletResponse response) {
        // New session id on login: defeats "session fixation" (an attacker planting a known id beforehand).
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                user, null, List.of(new SimpleGrantedAuthority(user.guest() ? "ROLE_GUEST" : "ROLE_USER")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);   // store it in the session
        return new Me(user);
    }
}
