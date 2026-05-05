package com.campustrade.platform.auth.controller;

import com.campustrade.platform.auth.service.AuthService;
import com.campustrade.platform.auth.dto.request.LoginRequestDTO;
import com.campustrade.platform.auth.dto.request.RegisterRequestDTO;
import com.campustrade.platform.auth.dto.request.ResetPasswordRequestDTO;
import com.campustrade.platform.auth.dto.request.SendCodeRequestDTO;
import com.campustrade.platform.auth.dto.response.AuthResponseDTO;
import com.campustrade.platform.auth.dto.response.SendCodeResponseDTO;
import com.campustrade.platform.common.ApiResponse;
import com.campustrade.platform.security.AuthUtils;
import com.campustrade.platform.user.dto.response.UserProfileResponseDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/email-code")
    public ApiResponse<SendCodeResponseDTO> sendCode(@Valid @RequestBody SendCodeRequestDTO request) {
        return ApiResponse.ok("验证码已发送", authService.sendVerificationCode(request));
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponseDTO> register(@Valid @RequestBody RegisterRequestDTO request) {
        return ApiResponse.ok("注册成功", authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return ApiResponse.ok("登录成功", authService.login(request));
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO request) {
        authService.resetPassword(request);
        return ApiResponse.ok("密码重置成功", null);
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponseDTO> me() {
        return ApiResponse.ok(authService.me(AuthUtils.currentUser()));
    }
}

