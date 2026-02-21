package br.com.clube_quinze.api.service.auth;

import br.com.clube_quinze.api.dto.auth.AuthResponse;
import br.com.clube_quinze.api.dto.auth.ForgotPasswordRequest;
import br.com.clube_quinze.api.dto.auth.LoginRequest;
import br.com.clube_quinze.api.dto.auth.RefreshTokenRequest;
import br.com.clube_quinze.api.dto.auth.RegisterRequest;
import br.com.clube_quinze.api.dto.auth.ResetPasswordRequest;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    void logout(RefreshTokenRequest request);

    void requestPasswordReset(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);
}
