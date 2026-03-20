package com.porcelain_shutter.bot.dto;

/**
 * The throne event that occurred after the match.
 */
public enum ThroneEvent {
    /**
     * Nobody has reached 100 MMR yet.
     */
    THRONE_EMPTY,
    /**
     * A sole leader has emerged (for the first time or a new one).
     */
    NEW_KING,
    /**
     * The leader has defended the throne - no changes.
     */
    KING_DEFENDED,
    /**
     * Several players with equal maximum MMR.
     */
    BATTLE,
    /**
     * The throne was already active, but no changes occurred.
     */
    NONE
}