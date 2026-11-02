package com.example.demo.service;

import com.example.demo.config.JwtProperties;
import com.example.demo.dto.AcceptInviteRequest;
import com.example.demo.dto.AccountResponse;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.ReadResponse;
import com.example.demo.entity.Invite;
import com.example.demo.entity.User;
import com.example.demo.exception.DuplicateUsernameException;
import com.example.demo.exception.InvalidCredentialsException;
import com.example.demo.exception.InvalidInviteException;
import com.example.demo.repository.InviteRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AuthService {

    private static final String PRIMARY = "primary (port 5432)";

    private final UserRepository userRepository;
    private final InviteRepository inviteRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository userRepository,
            InviteRepository inviteRepository,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.inviteRepository = inviteRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password");
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername()).orElse(null);
        String hash = user == null ? dummyPasswordHash : user.getPassword();
        if (user == null || !passwordEncoder.matches(request.getPassword(), hash)) {
            throw new InvalidCredentialsException();
        }

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.getIssuer())
                .audience(List.of(jwtProperties.getAudience()))
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.getAccessTokenTtl()))
                .subject(user.getUsername())
                .claim("roles", List.of(user.getRole().name()))
                .build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims)).getTokenValue();
        return new LoginResponse(accessToken, "Bearer", jwtProperties.getAccessTokenTtl().toSeconds());
    }

    @Transactional
    public ReadResponse<AccountResponse> acceptInvite(AcceptInviteRequest request) {
        Invite invite = inviteRepository.findByTokenHash(InviteTokens.hash(request.getToken()))
                .orElseThrow(InvalidInviteException::new);
        Instant now = Instant.now();
        if (invite.getConsumedAt() != null || !invite.getExpiresAt().isAfter(now)) {
            throw new InvalidInviteException();
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateUsernameException(request.getUsername());
        }

        User user = userRepository.save(new User(
                request.getUsername(),
                passwordEncoder.encode(request.getPassword()),
                invite.getRole()));
        invite.setConsumedAt(now);
        return new ReadResponse<>(PRIMARY, new AccountResponse(user.getUsername(), user.getRole().name()));
    }
}
