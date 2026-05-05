package com.campustrade.platform.message.controller;

import com.campustrade.platform.message.dto.request.SendMessageRequestDTO;
import com.campustrade.platform.message.dto.request.StartConversationRequestDTO;
import com.campustrade.platform.message.dto.response.ConversationResponseDTO;
import com.campustrade.platform.message.dto.response.MessageResponseDTO;
import com.campustrade.platform.message.service.MessageService;
import com.campustrade.platform.common.ApiResponse;
import com.campustrade.platform.common.PageResponse;
import com.campustrade.platform.security.AuthUtils;
import com.campustrade.platform.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

@PostMapping("/conversations")
    public ApiResponse<ConversationResponseDTO> startConversation(@Valid @RequestBody StartConversationRequestDTO request) {
        UserPrincipal principal = AuthUtils.currentUser();
        return ApiResponse.ok("会话已创建", messageService.startConversation(principal.userId(), request.goodsId()));
    }

    @GetMapping("/conversations")
    public ApiResponse<PageResponse<ConversationResponseDTO>> listConversations(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        UserPrincipal principal = AuthUtils.currentUser();
        return ApiResponse.ok(messageService.listConversations(principal.userId(), page, size));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<PageResponse<MessageResponseDTO>> listMessages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        UserPrincipal principal = AuthUtils.currentUser();
        return ApiResponse.ok(messageService.listMessages(principal.userId(), conversationId, page, size));
    }

    @PostMapping("/messages")
    public ApiResponse<MessageResponseDTO> sendMessage(@Valid @RequestBody SendMessageRequestDTO request) {
        UserPrincipal principal = AuthUtils.currentUser();
        return ApiResponse.ok("消息发送成功", messageService.sendMessage(principal.userId(), request));
    }
}

