package com.abhiman.taskflow.auth;

import com.abhiman.taskflow.auth.AuthDtos.LoginRequest;
import com.abhiman.taskflow.auth.AuthDtos.RegisterRequest;
import com.abhiman.taskflow.common.ConflictException;
import com.abhiman.taskflow.user.Role;
import com.abhiman.taskflow.user.User;
import com.abhiman.taskflow.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository users;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "hashed:" + inv.getArgument(0));
        authService = new AuthService(users, passwordEncoder, jwtService);
    }

    @Test
    void registerCreatesMemberWithHashedPasswordAndNormalizedEmail() {
        when(users.existsByEmailIgnoreCase("ada@example.com")).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User created = authService.register(new RegisterRequest(" Ada ", "  Ada@Example.com ", "s3cret-pass"));

        assertThat(created.getEmail()).isEqualTo("ada@example.com");
        assertThat(created.getFullName()).isEqualTo("Ada");
        assertThat(created.getRole()).isEqualTo(Role.MEMBER);
        assertThat(created.getPasswordHash()).isEqualTo("hashed:s3cret-pass");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(users.existsByEmailIgnoreCase("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("Ada", "ada@example.com", "s3cret-pass")))
                .isInstanceOf(ConflictException.class);
        verify(users, never()).save(any());
    }

    @Test
    void loginReturnsTokenForCorrectPassword() {
        User ada = new User("Ada", "ada@example.com", "stored-hash", Role.MEMBER);
        when(users.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(ada));
        when(passwordEncoder.matches("s3cret-pass", "stored-hash")).thenReturn(true);
        when(jwtService.issueToken(ada)).thenReturn("jwt");
        when(jwtService.expiresInSeconds()).thenReturn(3600L);

        AuthDtos.TokenResponse response = authService.login(new LoginRequest("ada@example.com", "s3cret-pass"));

        assertThat(response.accessToken()).isEqualTo("jwt");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void loginRejectsWrongPassword() {
        User ada = new User("Ada", "ada@example.com", "stored-hash", Role.MEMBER);
        when(users.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(ada));
        when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ada@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void loginForUnknownEmailStillChecksAPasswordAndGivesSameError() {
        when(users.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", "whatever")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
        // The dummy hash comparison keeps response time the same as for a real user.
        verify(passwordEncoder).matches("whatever", "hashed:timing-attack-dummy-password");
    }
}
