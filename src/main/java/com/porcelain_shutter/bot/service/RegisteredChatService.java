package com.porcelain_shutter.bot.service;

import com.porcelain_shutter.bot.entity.RegisteredChat;
import com.porcelain_shutter.bot.repository.RegisteredChatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisteredChatService {

    @Value("${telegram.bot.debug.enabled:false}")
    private boolean debugEnabled;

    @Value("${telegram.bot.debug.chat-id:}")
    private long debugChatId;

    private final RegisteredChatRepository repository;

    /**
     * Returns a list of chatIds to which the bot will send messages.
     * <p>
     * Debug mode: returns only the debugChatId from application.properties.
     * Normal mode: returns all active registered chats from the database.
     */
    @Transactional(readOnly = true)
    public List<Long> getActiveChatIds() {
        if (debugEnabled) {
            log.debug("[Chat] Debug mode active — using debugChatId={}", debugChatId);
            return List.of(debugChatId);
        }
        return repository.findAllByActiveTrue().stream()
                .map(RegisteredChat::getChatId)
                .toList();
    }

    /**
     * Registers a chat when adding a bot or sending the first message.
     */
    @Transactional
    public void registerChat(Long chatId, String chatTitle) {
        repository.findById(chatId).ifPresentOrElse(
                chat -> {
                    if (!chat.isActive()) {
                        chat.setActive(true);
                        chat.setChatTitle(chatTitle);
                        repository.save(chat);
                        log.info("[Chat] Re-activated chatId={} title='{}'", chatId, chatTitle);
                    }
                },
                () -> {
                    repository.save(RegisteredChat.builder()
                            .chatId(chatId)
                            .chatTitle(chatTitle)
                            .registeredAt(LocalDateTime.now())
                            .active(true)
                            .build());
                    log.info("[Chat] Registered new chatId={} title='{}'", chatId, chatTitle);
                }
        );
    }

    /**
     * Deactivates chat when the bot is removed from the group.
     */
    @Transactional
    public void deactivateChat(Long chatId) {
        repository.findById(chatId).ifPresent(chat -> {
            chat.setActive(false);
            repository.save(chat);
            log.info("[Chat] Deactivated chatId={}", chatId);
        });
    }
}