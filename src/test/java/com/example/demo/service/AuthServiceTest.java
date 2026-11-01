package com.example.demo.service;

import com.example.demo.config.JwtProperties;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.exception.InvalidCredentialsException;
import com.example.demo.repository.UserRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

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
        authService = new AuthService(userRepository, passwordEncoder, jwtEncoder, properties);
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

    private LoginRequest request(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }
}
