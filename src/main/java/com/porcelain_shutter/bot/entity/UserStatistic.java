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

@Entity
@Table(
        name = "user_statistics",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_stat_chat_user", columnNames = {"chat_id", "user_id"}),
        indexes = {@Index(name = "idx_user_stat_chat_id", columnList = "chat_id")}
)
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class UserStatistic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "username", length = 100)
    private String username;

    // ── Forecasts ──────────────────────────────────────────────────────────
    @Column(name = "correct_predictions", nullable = false)
    private long correctPredictions;
    @Column(name = "wrong_predictions", nullable = false)
    private long wrongPredictions;
    @Column(name = "win_votes", nullable = false)
    private long winVotes;
    @Column(name = "lose_votes", nullable = false)
    private long loseVotes;

    // ── Predictor MMR — старт с 0, +25/-15, мин. 0 ───────────────────────
    @Column(name = "predictor_mmr", nullable = false)
    private long predictorMmr;

    // ── 322 statistics ────────────────────────────────────────────────────
    @Column(name = "betrayal_correct", nullable = false)
    private long betrayalCorrect;
    @Column(name = "betrayal_wrong", nullable = false)
    private long betrayalWrong;

    // ── Серия: >0 = победы подряд, <0 = поражения подряд ─────────────────
    @Column(name = "current_streak", nullable = false)
    private int currentStreak;

    // ── Last vote ───────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "last_vote_type", length = 10)
    private VoteType lastVoteType;
    @Column(name = "last_vote_correct")
    private Boolean lastVoteCorrect;
    @Column(name = "last_vote_in_match")
    private Boolean lastVoteInMatch;
    @Column(name = "last_vote_was_betrayal")
    private Boolean lastVoteWasBetrayal;

    // ── Helpers ───────────────────────────────────────────────────────────

    public long totalBetrayals() {
        return betrayalCorrect + betrayalWrong;
    }

    public long totalPredictions() {
        return correctPredictions + wrongPredictions;
    }

    public long correctPercent() {
        long total = totalPredictions();
        return total == 0 ? 0 : Math.round(100.0 * correctPredictions / total);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof UserStatistic that)) return false;
        return getId() != null && getId().equals(that.getId());
    }

    @Override
    public final int hashCode() {
        return UserStatistic.class.hashCode();
    }
}