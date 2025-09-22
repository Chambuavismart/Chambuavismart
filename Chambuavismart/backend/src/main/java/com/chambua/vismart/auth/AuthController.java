package com.chambua.vismart.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        // Very basic stub: accept email "user@demo.com" and password length >= 6
        if (request == null || request.getEmail() == null || request.getPassword() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid credentials"));
        }

        String email = request.getEmail().trim().toLowerCase();
        String password = request.getPassword();

        if ("user@demo.com".equals(email) && password.length() >= 6) {
            // Return a dummy JWT-like string and expiry in epoch millis
            String token = "dummy.jwt.token.for.demo";
            long expiresAt = Instant.now().plus(1, ChronoUnit.HOURS).toEpochMilli();
            return ResponseEntity.ok(new LoginResponse(token, expiresAt));
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Invalid credentials"));
    }
}
