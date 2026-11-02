package com.example.demo.controller;

import com.example.demo.config.JwtProperties;
import com.example.demo.config.SecurityConfig;
import com.example.demo.dto.AccountResponse;
import com.example.demo.dto.AuthorResponse;
import com.example.demo.dto.BookPageResponse;
import com.example.demo.dto.BookResponse;
import com.example.demo.dto.InviteResponse;
import com.example.demo.dto.MessageResponse;
import com.example.demo.dto.PageInfo;
import com.example.demo.dto.ReadResponse;
import com.example.demo.exception.InvalidCredentialsException;
import com.example.demo.service.AuthService;
import com.example.demo.service.AuthorService;
import com.example.demo.service.BookService;
import com.example.demo.service.InviteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {BookController.class, AuthorController.class, AuthController.class, InviteController.class})
@Import(SecurityConfig.class)
class ApiSecurityTest {

    private static final String BOOK_JSON = "{\"name\":\"Dune\",\"authorId\":1}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @MockitoBean
    private BookService bookService;

    @MockitoBean
    private AuthorService authorService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private InviteService inviteService;

    @BeforeEach
    void setUp() {
        when(bookService.getAllBooks(any(), nullable(String.class), nullable(String.class)))
                .thenReturn(new BookPageResponse("replica (port 5433)", List.of(), new PageInfo(0, 10, 0, 0, true, true)));
        when(bookService.getBookById(1L))
                .thenReturn(new ReadResponse<>("replica (port 5433)", new BookResponse(1L, "Dune", new AuthorResponse(1L, "Frank", "US"))));
        when(bookService.createBook(any()))
                .thenReturn(new ReadResponse<>("primary (port 5432)", new BookResponse(1L, "Dune", new AuthorResponse(1L, "Frank", "US"))));
        when(bookService.updateBook(any(), any()))
                .thenReturn(new ReadResponse<>("primary (port 5432)", new BookResponse(1L, "Dune", new AuthorResponse(1L, "Frank", "US"))));
        when(bookService.deleteBook(1L))
                .thenReturn(new MessageResponse("primary (port 5432)", "Book deleted successfully"));
        when(authorService.createAuthor(any()))
                .thenReturn(new ReadResponse<>("primary (port 5432)", new AuthorResponse(1L, "Frank", "US")));
        when(inviteService.createInvite(any()))
                .thenReturn(new ReadResponse<>("primary (port 5432)", new InviteResponse(1L, "person@example.com", "STAFF", Instant.parse("2026-11-04T00:00:00Z"))));
        when(authService.acceptInvite(any()))
                .thenReturn(new ReadResponse<>("primary (port 5432)", new AccountResponse("alice", "STAFF")));
    }

    @Test
    void getBooksWithoutTokenIsOk() throws Exception {
        mockMvc.perform(get("/api/v1/books"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/books/1"))
                .andExpect(status().isOk());
    }

    @Test
    void createAuthorWithoutTokenIsOk() throws Exception {
        mockMvc.perform(post("/api/v1/authors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Frank\",\"country\":\"US\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void bookWritesWithoutTokenAreUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/books").contentType(MediaType.APPLICATION_JSON).content(BOOK_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Unauthorized"));
        mockMvc.perform(put("/api/v1/books/1").contentType(MediaType.APPLICATION_JSON).content(BOOK_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/books/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bookWritesWithBadTokenAreUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/books")
                        .header("Authorization", "Bearer not-a-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOK_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    @Test
    void bookWritesAsUserAreForbidden() throws Exception {
        String token = token("reader", "USER");
        mockMvc.perform(post("/api/v1/books")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOK_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Forbidden"));
        mockMvc.perform(put("/api/v1/books/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOK_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/books/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void bookWritesAsStaffAreOk() throws Exception {
        String token = token("staff", "STAFF");
        mockMvc.perform(post("/api/v1/books")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOK_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Dune"));
        mockMvc.perform(put("/api/v1/books/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOK_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/books/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void unknownPathWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/unknown"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRejectsInvalidCredentials() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"staff\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void loginRejectsBlankUsername() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"secret\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createInviteWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/invites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"person@example.com\",\"role\":\"STAFF\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createInviteAsUserIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/invites")
                        .header("Authorization", "Bearer " + token("reader", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"person@example.com\",\"role\":\"STAFF\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Forbidden"));
    }

    @Test
    void createInviteAsStaffIsOk() throws Exception {
        mockMvc.perform(post("/api/v1/invites")
                        .header("Authorization", "Bearer " + token("staff", "STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"person@example.com\",\"role\":\"STAFF\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("person@example.com"))
                .andExpect(jsonPath("$.data.role").value("STAFF"))
                .andExpect(jsonPath("$.data.token").doesNotExist());
    }

    @Test
    void acceptInviteWithoutTokenIsOk() throws Exception {
        mockMvc.perform(post("/api/v1/auth/accept-invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"raw-token\",\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andExpect(jsonPath("$.data.role").value("STAFF"));
    }

    private String token(String username, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.getIssuer())
                .audience(List.of(jwtProperties.getAudience()))
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.getAccessTokenTtl()))
                .subject(username)
                .claim("roles", List.of(role))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims)).getTokenValue();
    }
}
