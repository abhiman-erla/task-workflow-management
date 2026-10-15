package com.abhiman.taskflow.auth;

import com.abhiman.taskflow.auth.AuthDtos.LoginRequest;
import com.abhiman.taskflow.auth.AuthDtos.RegisterRequest;
import com.abhiman.taskflow.auth.AuthDtos.TokenResponse;
import com.abhiman.taskflow.common.ConflictException;
import com.abhiman.taskflow.user.Role;
import com.abhiman.taskflow.user.User;
import com.abhiman.taskflow.user.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    // Hash of a random string, compared against when the email doesn't exist. This makes
    // "unknown email" take as long as "wrong password", so response timing can't reveal
    // which emails are registered.
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("timing-attack-dummy-password");
    }

    @Transactional
    public User register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email is already registered");
        }
        // Self-registration can never grant elevated roles; admins promote users later.
        return users.save(new User(request.fullName().trim(), email,
                passwordEncoder.encode(request.password()), Role.MEMBER));
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Optional<User> user = users.findByEmailIgnoreCase(normalize(request.email()));
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !passwordMatches) {
            // Same message for both cases: don't tell an attacker which part was wrong.
            throw new BadCredentialsException("Invalid email or password");
        }
        return new TokenResponse(jwtService.issueToken(user.get()), "Bearer", jwtService.expiresInSeconds());
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
