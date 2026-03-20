package com.porcelain_shutter.bot.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Input payload for POST /api/vote/start.
 * Carries match metadata, team rosters and optional commentary
 * that will be displayed in the Telegram poll message.
 */
@Data
public class MatchStartRequest {

    /**
     * Unique match identifier — stored in poll_sessions and votes for traceability.
     */
    @NotBlank(message = "matchId must not be blank")
    private String matchId;

    /**
     * Exactly two teams: Radiant and Dire.
     */
    @Valid
    @NotEmpty(message = "teams must not be empty")
    @Size(min = 2, max = 2, message = "teams must contain exactly 2 entries (Radiant and Dire)")
    private List<Team> teams;

    /**
     * Optional analytical / prediction comment shown below team rosters.
     * Example: "Radiant has a 65% win rate on this patch"
     */
    @Size(max = 500, message = "predictionComment must not exceed 500 characters")
    private String predictionComment;

    /**
     * Optional meme / fun comment shown at the bottom of the poll message.
     * Example: "Хто не ризикує — той не п'є шампанського 🥂"
     */
    @Size(max = 300, message = "memeComment must not exceed 300 characters")
    private String memeComment;
}