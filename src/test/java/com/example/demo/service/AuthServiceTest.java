package com.example.demo.service;

import com.example.demo.config.JwtProperties;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.AcceptInviteRequest;
import com.example.demo.dto.AccountResponse;
import com.example.demo.dto.ReadResponse;
import com.example.demo.entity.Invite;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.exception.DuplicateUsernameException;
import com.example.demo.exception.InvalidCredentialsException;
import com.example.demo.exception.InvalidInviteException;
import com.example.demo.repository.InviteRepository;
import com.example.demo.repository.UserRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private InviteRepository inviteRepository;

    private PasswordEncoder passwordEncoder;
    private JwtDecoder jwtDecoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        JwtProperties properties = new JwtProperties();
        properties.setSecret("demo-secret-key-must-be-32-bytes!");
        properties.setAccessTokenTtl(Duration.ofMinutes(15));
        properties.setIssuer("demo");
        properties.setAudience("demo-api");

        SecretKey key = new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        jwtDecoder = NimbusJwtDecoder.withSecretKey(key).build();
        authService = new AuthService(userRepository, inviteRepository, passwordEncoder, jwtEncoder, properties);
    }

    @Test
    void loginReturnsStaffRoleInAccessToken() {
        User staff = new User("staff", passwordEncoder.encode("staffpass"), Role.STAFF);
        when(userRepository.findByUsername("staff")).thenReturn(Optional.of(staff));

        LoginResponse response = authService.login(request("staff", "staffpass"));

        Jwt jwt = jwtDecoder.decode(response.getAccessToken());
        assertEquals("staff", jwt.getSubject());
        assertEquals(List.of("demo-api"), jwt.getAudience());
        assertEquals(List.of("STAFF"), jwt.getClaim("roles"));
        assertEquals("Bearer", response.getTokenType());
        assertEquals(900, response.getExpiresIn());
    }

    @Test
    void loginRejectsUnknownUser() {
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request("missing", "staffpass")));
    }

    @Test
    void loginRejectsWrongPassword() {
        User staff = new User("staff", passwordEncoder.encode("staffpass"), Role.STAFF);
        when(userRepository.findByUsername("staff")).thenReturn(Optional.of(staff));

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request("staff", "wrong")));
    }

    @Test
    void acceptInviteCreatesUserWithRoleFromInvite() {
        Invite invite = new Invite("person@example.com", Role.STAFF, InviteTokens.hash("raw-token"), Instant.now().plus(Duration.ofHours(1)));
        when(inviteRepository.findByTokenHash(InviteTokens.hash("raw-token"))).thenReturn(Optional.of(invite));
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReadResponse<AccountResponse> response = authService.acceptInvite(accept("raw-token", "alice", "password123"));

        assertEquals("primary (port 5432)", response.getDataSource());
        assertEquals("alice", response.getData().getUsername());
        assertEquals("STAFF", response.getData().getRole());
        assertNotNull(invite.getConsumedAt());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void acceptInviteStoresEncodedPassword() {
        Invite invite = new Invite("person@example.com", Role.USER, InviteTokens.hash("raw-token"), Instant.now().plus(Duration.ofHours(1)));
        when(inviteRepository.findByTokenHash(InviteTokens.hash("raw-token"))).thenReturn(Optional.of(invite));
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        authService.acceptInvite(accept("raw-token", "alice", "password123"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertTrue(passwordEncoder.matches("password123", captor.getValue().getPassword()));
        assertEquals(Role.USER, captor.getValue().getRole());
    }

    @Test
    void acceptInviteRejectsUnknownExpiredOrConsumedToken() {
        when(inviteRepository.findByTokenHash(InviteTokens.hash("missing"))).thenReturn(Optional.empty());
        assertThrows(InvalidInviteException.class, () -> authService.acceptInvite(accept("missing", "alice", "password123")));

        Invite expired = new Invite("person@example.com", Role.STAFF, InviteTokens.hash("expired"), Instant.now().minus(Duration.ofMinutes(1)));
        when(inviteRepository.findByTokenHash(InviteTokens.hash("expired"))).thenReturn(Optional.of(expired));
        assertThrows(InvalidInviteException.class, () -> authService.acceptInvite(accept("expired", "alice", "password123")));

        Invite consumed = new Invite("person@example.com", Role.STAFF, InviteTokens.hash("used"), Instant.now().plus(Duration.ofHours(1)));
        consumed.setConsumedAt(Instant.now());
        when(inviteRepository.findByTokenHash(InviteTokens.hash("used"))).thenReturn(Optional.of(consumed));
        assertThrows(InvalidInviteException.class, () -> authService.acceptInvite(accept("used", "alice", "password123")));

        verify(userRepository, never()).save(any());
    }

    @Test
    void acceptInviteKeepsInviteWhenUsernameExists() {
        Invite invite = new Invite("person@example.com", Role.STAFF, InviteTokens.hash("raw-token"), Instant.now().plus(Duration.ofHours(1)));
        when(inviteRepository.findByTokenHash(InviteTokens.hash("raw-token"))).thenReturn(Optional.of(invite));
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThrows(DuplicateUsernameException.class, () -> authService.acceptInvite(accept("raw-token", "alice", "password123")));
        assertNull(invite.getConsumedAt());
        verify(userRepository, never()).save(any());
    }

    private AcceptInviteRequest accept(String token, String username, String password) {
        AcceptInviteRequest request = new AcceptInviteRequest();
        request.setToken(token);
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private LoginRequest request(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }
}
