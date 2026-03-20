package com.porcelain_shutter.bot.dto;


import java.util.List;

/**
 * Match processing result: voter statistics + throne status.
 * <p>
 * Used to generate the final match message in BotMessages.
 */
public record ThroneGame(

        /** Players who voted for the winning side (with updated MMR). */
        List<VoterStat> winVoters,

        /** Players who voted for the losing side (with updated MMR). */
        List<VoterStat> loseVoters,

        /** Post-match throne event. */
        ThroneEvent event,

        /** Current leader's name (for NEW_KING / SINGLE_KING / KING_DEFENDED). null if the throne is empty or there is a battle. */
        String kingName,

        /** Leader's MMR. */
        long kingMmr,

        /** Battle participants (for BATTLE). */
        List<String> battleContenders,

        /** Contenders for the throne - within 25 MMR of the leader (for CHALLENGER). */
        List<String> challengers,

        /** Name of the previous king (for NEW_KING when the old one was reset). */
        String deposedKingName,

        /** Was there at least one traitor (322) in this match. */
        boolean has322,

        /** Did the traitors put it correctly? (to select a phrase). */
        boolean betrayalCorrect
) {
}