package com.campustrade.platform.message.service;

import com.campustrade.platform.common.AppException;
import com.campustrade.platform.common.PageResponse;
import com.campustrade.platform.goods.dataobject.GoodsDO;
import com.campustrade.platform.goods.service.GoodsService;
import com.campustrade.platform.message.assembler.ConversationAssembler;
import com.campustrade.platform.message.assembler.MessageAssembler;
import com.campustrade.platform.message.dataobject.ConversationDO;
import com.campustrade.platform.message.dataobject.MessageDO;
import com.campustrade.platform.message.dto.request.SendMessageRequestDTO;
import com.campustrade.platform.message.dto.response.ConversationResponseDTO;
import com.campustrade.platform.message.dto.response.MessageResponseDTO;
import com.campustrade.platform.message.mapper.ConversationMapper;
import com.campustrade.platform.message.mapper.MessageMapper;
import com.campustrade.platform.user.dataobject.UserDO;
import com.campustrade.platform.user.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MessageService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final GoodsService goodsService;
    private final UserService userService;
    private final ConversationAssembler conversationAssembler;
    private final MessageAssembler messageAssembler;

    public MessageService(ConversationMapper conversationMapper,
                          MessageMapper messageMapper,
                          GoodsService goodsService,
                          UserService userService,
                          ConversationAssembler conversationAssembler,
                          MessageAssembler messageAssembler) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.goodsService = goodsService;
        this.userService = userService;
        this.conversationAssembler = conversationAssembler;
        this.messageAssembler = messageAssembler;
    }

@Transactional
    public ConversationResponseDTO startConversation(Long buyerId, Long goodsId) {
        userService.getById(buyerId);
        GoodsDO goods = goodsService.getById(goodsId);
        if (goods.getSeller() != null && goods.getSeller().getId().equals(buyerId)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "不能和自己发起会话");
        }

        ConversationDO conversation = conversationMapper.findByGoodsIdAndBuyerId(goodsId, buyerId);
        if (conversation == null) {
            ConversationDO created = new ConversationDO();
            created.setGoodsId(goodsId);
            created.setBuyerId(buyerId);
            created.setSellerId(goods.getSeller().getId());
            conversationMapper.insert(created);
            conversation = conversationMapper.findById(created.getId());
        }

        return conversationAssembler.toResponse(conversation);
    }

    @Transactional
    public MessageResponseDTO sendMessage(Long userId, SendMessageRequestDTO request) {
        UserDO sender = userService.getById(userId);
        ConversationDO conversation = getConversationOrThrow(request.conversationId());
        validateMembership(sender, conversation);

        MessageDO message = new MessageDO();
        message.setConversationId(conversation.getId());
        message.setSenderId(sender.getId());
        message.setContent(request.content().trim());
        messageMapper.insert(message);

        conversationMapper.updateLastMessageAt(conversation.getId(), LocalDateTime.now());

        MessageDO saved = messageMapper.findById(message.getId());
        return messageAssembler.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversationResponseDTO> listConversations(Long userId, int page, int size) {
        userService.getById(userId);
        int offset = page * size;

        List<ConversationDO> conversations = conversationMapper.listByBuyerOrSeller(userId, size, offset);
        long total = conversationMapper.countByBuyerOrSeller(userId);

        attachCoverImages(conversations);

        List<ConversationResponseDTO> items = conversations.stream().map(conversationAssembler::toResponse).toList();
        return PageResponse.of(items, total, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<MessageResponseDTO> listMessages(Long userId, Long conversationId, int page, int size) {
        UserDO user = userService.getById(userId);
        ConversationDO conversation = getConversationOrThrow(conversationId);
        validateMembership(user, conversation);

        int offset = page * size;
        List<MessageDO> messages = messageMapper.listByConversationId(conversationId, size, offset);
        long total = messageMapper.countByConversationId(conversationId);

        List<MessageResponseDTO> items = messages.stream().map(messageAssembler::toResponse).toList();
        return PageResponse.of(items, total, page, size);
    }

    private ConversationDO getConversationOrThrow(Long conversationId) {
        ConversationDO conversation = conversationMapper.findById(conversationId);
        if (conversation == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "会话不存在");
        }
        return conversation;
    }

    private void validateMembership(UserDO user, ConversationDO conversation) {
        boolean member = user.getId().equals(conversation.getBuyer().getId())
                || user.getId().equals(conversation.getSeller().getId());
        if (!member) {
            throw new AppException(HttpStatus.FORBIDDEN, "无权访问该会话");
        }
    }

    private void attachCoverImages(List<ConversationDO> conversations) {
        if (conversations == null || conversations.isEmpty()) {
            return;
        }
        List<Long> goodsIds = conversations.stream()
                .map(ConversationDO::getGoodsId)
                .distinct()
                .toList();
        if (goodsIds.isEmpty()) {
            return;
        }
        List<Map<String, Object>> rows = conversationMapper.findCoverImagesByGoodsIds(goodsIds);
        Map<Long, String> coverMap = rows.stream()
                .collect(Collectors.toMap(
                        r -> ((Number) r.get("goodsId")).longValue(),
                        r -> (String) r.get("imageUrl"),
                        (a, b) -> a
                ));
        for (ConversationDO conv : conversations) {
            conv.setGoodsCoverImage(coverMap.get(conv.getGoodsId()));
        }
    }
}
