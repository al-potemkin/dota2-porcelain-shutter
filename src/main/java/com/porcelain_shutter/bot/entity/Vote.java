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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "votes",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_votes_match_chat_user",
                columnNames = {"match_id", "chat_id", "user_id"}
        ),
        indexes = {
                @Index(name = "idx_votes_match_id", columnList = "match_id"),
                @Index(name = "idx_votes_chat_id", columnList = "chat_id")
        }
)
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Links this vote to a specific match — matches PollSession.matchId.
     */
    @Column(name = "match_id", nullable = false)
    private String matchId;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * Telegram username — сохраняется для отображения в статистике
     */
    @Column(name = "username", length = 100)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "vote_type", nullable = false, length = 10)
    private VoteType voteType;

    @Column(name = "voted_at", nullable = false)
    private LocalDateTime votedAt;

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Vote that)) return false;
        return getId() != null && getId().equals(that.getId());
    }

    @Override
    public final int hashCode() {
        return Vote.class.hashCode();
    }
}