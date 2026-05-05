package com.campustrade.platform.user.controller;

import com.campustrade.platform.common.ApiResponse;
import com.campustrade.platform.security.AuthUtils;
import com.campustrade.platform.security.UserPrincipal;
import com.campustrade.platform.user.assembler.UserProfileAssembler;
import com.campustrade.platform.user.dto.request.UpdateProfileRequestDTO;
import com.campustrade.platform.user.dto.response.UserProfileResponseDTO;
import com.campustrade.platform.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final UserProfileAssembler userProfileAssembler;

    public UserController(UserService userService, UserProfileAssembler userProfileAssembler) {
        this.userService = userService;
        this.userProfileAssembler = userProfileAssembler;
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponseDTO> me() {
        UserPrincipal principal = AuthUtils.currentUser();
        return ApiResponse.ok(userProfileAssembler.toResponse(userService.getById(principal.userId())));
    }

    @PutMapping("/me")
    public ApiResponse<UserProfileResponseDTO> updateProfile(@Valid @RequestBody UpdateProfileRequestDTO request) {
        UserPrincipal principal = AuthUtils.currentUser();
        return ApiResponse.ok("个人资料更新成功", userProfileAssembler.toResponse(userService.updateProfile(principal.userId(), request)));
    }
}

