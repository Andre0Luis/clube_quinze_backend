package br.com.clube_quinze.api.dto.auth;

import java.io.Serializable;

public record AuthResponse(String accessToken, String refreshToken, String tokenType) implements Serializable {

    public static AuthResponse bearer(String accessToken, String refreshToken) {
        return new AuthResponse(accessToken, refreshToken, "Bearer");
    }
}
