package com.porcelain_shutter.bot.entity;

public enum MatchStatus {
    /**
     * Voting is open, buttons are visible.
     */
    ACTIVE,
    /**
     * 3 minutes elapsed — buttons removed, percentage results shown.
     */
    VOTING_CLOSED,
    /**
     * summarizeVote called — winner revealed in Telegram.
     */
    SUMMARIZED
}
