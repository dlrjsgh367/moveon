package io.moveon.auth;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AuthController {

    private final MemberRepository members;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(MemberRepository members, PasswordEncoder encoder, JwtService jwt) {
        this.members = members;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public record SignupRequest(String email, String password) {
    }

    public record LoginRequest(String email, String password) {
    }

    @PostMapping("/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> signup(@RequestBody SignupRequest req) {
        requireText(req.email(), "email");
        requireText(req.password(), "password");
        if (members.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 이메일");
        }
        Member saved = members.save(new Member(req.email(), encoder.encode(req.password())));
        return Map.of("id", saved.getId(), "email", saved.getEmail());
    }

    @PostMapping("/auth/login")
    public Map<String, String> login(@RequestBody LoginRequest req) {
        requireText(req.email(), "email");
        requireText(req.password(), "password");
        Member member = members.findByEmail(req.email())
                .filter(m -> encoder.matches(req.password(), m.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않음"));
        return Map.of("token", jwt.issue(member.getId(), member.getEmail()));
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " 필수");
        }
    }
}
