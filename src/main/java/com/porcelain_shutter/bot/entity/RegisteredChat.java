package com.porcelain_shutter.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Chat in which the bot is present.
 * Entry is created when the bot is added to the group or receives its first message.
 * Deleted (active = false) when the bot is removed from the group.
 */
@Entity
@Table(
        name = "registered_chats",
        indexes = @Index(name = "idx_registered_chats_active", columnList = "active")
)
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class RegisteredChat {

    @Id
    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "chat_title")
    private String chatTitle;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RegisteredChat that)) return false;
        return getChatId() != null && getChatId().equals(that.getChatId());
    }

    @Override
    public final int hashCode() {
        return RegisteredChat.class.hashCode();
    }
}