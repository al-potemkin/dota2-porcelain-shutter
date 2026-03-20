package com.porcelain_shutter.bot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Input payload for POST /api/vote/summarize.
 * Sent when the match has concluded — triggers the final
 * summary message in Telegram showing vote results vs. actual outcome.
 */
@Data
public class MatchEndRequest {

    /**
     * Must match the matchId from the corresponding MatchStartRequest.
     */
    @NotBlank(message = "matchId must not be blank")
    private String matchId;

    /**
     * Name of the team that actually won.
     * Should match one of the teamSide values from the original teams list
     * (e.g. "Radiant" or "Dire").
     */
    @NotBlank(message = "teamWon must not be blank")
    private String teamWon;
}