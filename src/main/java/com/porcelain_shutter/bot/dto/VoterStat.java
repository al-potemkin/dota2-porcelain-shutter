package com.porcelain_shutter.bot.dto;

import com.porcelain_shutter.bot.entity.VoteType;

/**
 * Statistics for one voter to display in the final message.
 */
public record VoterStat(String displayName, long mmrAfter, VoteType voteType) {
}