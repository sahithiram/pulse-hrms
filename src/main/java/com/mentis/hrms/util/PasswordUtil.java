package com.mentis.hrms.util;

import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class PasswordUtil {

    // Simple SHA-256 password hashing
    public String encodePassword(String rawPassword) {
        if (rawPassword == null) return null;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            String encoded = Base64.getEncoder().encodeToString(hash);


            return encoded;
        } catch (Exception e) {
            // Fallback to simple Base64 encoding
            String fallback = Base64.getEncoder().encodeToString(
                    rawPassword.getBytes(StandardCharsets.UTF_8));
            return fallback;
        }
    }

    public boolean matchesPassword(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }


        String encodedInput = encodePassword(rawPassword);
        boolean matches = encodedInput.equals(storedPassword);


        return matches;
    }
}
