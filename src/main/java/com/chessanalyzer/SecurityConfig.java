package com.chessanalyzer;

import com.chessanalyzer.user.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;

/**
 * Spring Security setup. Every HTTP request passes through a chain of security
 * filters BEFORE it reaches a controller; this class configures that chain.
 *
 * - Sessions: after login the server remembers you in an HTTP session; the browser
 *   only holds a random session id in an HttpOnly cookie (JavaScript can't read it,
 *   so an XSS bug can't steal it).
 * - CSRF protection: because the browser sends that cookie automatically, another
 *   website could try to make your browser POST to us. csrf.spa() defends against
 *   that: the server puts a token in a readable XSRF-TOKEN cookie, and our own
 *   JavaScript must copy it into an X-XSRF-TOKEN header on every POST. Other sites
 *   can't read our cookies, so they can't forge the header.
 * - Access rules: analysis tools stay public; online play needs a login (or guest).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.spa())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/online/**", "/ws/**").authenticated()
                        .anyRequest().permitAll())
                // An API should answer 401, not redirect to an HTML login page.
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .formLogin(AbstractHttpConfigurer::disable)   // our React page does login via JSON instead
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.OK)));
        return http.build();
    }

    /** Stores hashes like "{bcrypt}$2a$10$..." (the prefix says which algorithm, so it can change later). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** How Spring Security finds a user by name for password login. Guests can't log in with a password. */
    @Bean
    public UserDetailsService userDetailsService(UserRepository users) {
        return username -> users.findByUsernameIgnoreCase(username)
                .filter(u -> !u.isGuest())
                .map(u -> User.withUsername(u.getUsername()).password(u.getPasswordHash()).roles("USER").build())
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }

    /** Checks a username + password against the database (used by AuthService.login). */
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
}
