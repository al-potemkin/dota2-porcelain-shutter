package com.porcelain_shutter.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Mapping between a Telegram user and their Dota 2 nickname.
 * A user can have multiple nicknames within a single chat.
 */
@Entity
@Table(
        name = "dota_profiles",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_dota_profile_chat_user_nick",
                columnNames = {"chat_id", "user_id", "dota_nickname"}
        ),
        indexes = {
                @Index(name = "idx_dota_profile_chat_user", columnList = "chat_id, user_id"),
                @Index(name = "idx_dota_profile_dota_nick", columnList = "chat_id, dota_nickname")
        }
)
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class DotaProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * Telegram @username — can be null if the user doesn't have a username
     */
    @Column(name = "telegram_username", length = 100)
    private String telegramUsername;

    /**
     * Nickname in Dota 2 - one user can have several
     */
    @Column(name = "dota_nickname", nullable = false, length = 100)
    private String dotaNickname;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DotaProfile that)) return false;
        return getId() != null && getId().equals(that.getId());
    }

    @Override
    public final int hashCode() {
        return DotaProfile.class.hashCode();
    }
}