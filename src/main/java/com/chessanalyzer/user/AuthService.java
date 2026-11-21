package com.chessanalyzer.user;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Creating accounts and checking passwords. Starting the HTTP session afterwards is
 * the controller's job (it has the request/response), see AuthController.
 */
@Service
public class AuthService {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_-]{3,20}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    public UserPrincipal signup(String username, String password) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new IllegalArgumentException("Username: 3-20 letters, digits, _ or -");
        }
        if (username.toLowerCase().startsWith("guest")) {
            throw new IllegalArgumentException("Usernames starting with 'guest' are reserved");
        }
        // BCrypt only looks at the first 72 bytes, so longer passwords would be silently cut.
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new IllegalArgumentException("Password: 8-72 characters");
        }
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new UsernameTakenException(username);
        }
        User user = users.save(User.registered(username, passwordEncoder.encode(password)));
        return UserPrincipal.of(user);
    }

    /**
     * Lets Spring Security check the password (AuthenticationManager -> DaoAuthenticationProvider
     * -> our UserDetailsService + PasswordEncoder). Wrong password -> BadCredentialsException.
     */
    public UserPrincipal login(String username, String password) {
        authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(username, password));
        return UserPrincipal.of(users.findByUsernameIgnoreCase(username).orElseThrow());
    }

    /** A throw-away account named like "Guest-4821". */
    public UserPrincipal createGuest() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String name = "Guest-" + (1000 + RANDOM.nextInt(9000));
            if (!users.existsByUsernameIgnoreCase(name)) {
                return UserPrincipal.of(users.save(User.guest(name)));
            }
        }
        throw new IllegalStateException("Could not find a free guest name, try again");
    }
}
