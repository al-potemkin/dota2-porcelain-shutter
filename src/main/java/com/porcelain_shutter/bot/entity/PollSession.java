package com.porcelain_shutter.bot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
 * Represents a voting session tied to a specific Dota match.
 * <p>
 * Lifecycle: ACTIVE  → (3 min) → VOTING_CLOSED (results posted) → SUMMARIZED (match outcome posted)
 */
@Entity
@Table(
        name = "poll_sessions",
        indexes = {
                @Index(name = "idx_poll_sessions_match_id", columnList = "match_id"),
                @Index(name = "idx_poll_sessions_chat_status", columnList = "chat_id, match_status")
        }
)
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PollSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * External match identifier provided by the caller.
     */
    @Column(name = "match_id", nullable = false)
    private String matchId;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    /**
     * Telegram message ID — used to edit the message when the poll closes.
     */
    @Column(name = "message_id", nullable = false)
    private Integer messageId;

    /**
     * True if the poll was sent as a photo (SendPhoto).
     * Determines whether to use EditMessageCaption vs EditMessageText on subsequent edits.
     */
    @Column(name = "has_photo", nullable = false)
    private boolean hasPhoto;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    /**
     * JSON-serialized match team list.
     * Stored to determine a player's "own" team during voting.
     * Format: JSON array of TeamDto.
     */
    @Column(name = "teams_json", columnDefinition = "TEXT")
    private String teamsJson;

    /**
     * The meme comment from the starting request is displayed in the voting closing message.
     */
    @Column(name = "meme_comment", length = 300)
    private String memeComment;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 20)
    private MatchStatus matchStatus;

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PollSession that)) return false;
        return getId() != null && getId().equals(that.getId());
    }

    @Override
    public final int hashCode() {
        return PollSession.class.hashCode();
    }
}