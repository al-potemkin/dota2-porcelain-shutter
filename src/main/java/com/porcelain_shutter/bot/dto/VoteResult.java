package com.porcelain_shutter.bot.dto;

public record VoteResult(long wins, long loses) {
    public long totalVotes() {
        return wins + loses;
    }

    public long winPercent() {
        return totalVotes() == 0 ? 0 : Math.round(100.0 * wins / totalVotes());
    }

    public long losePercent() {
        return totalVotes() == 0 ? 0 : Math.round(100.0 * loses / totalVotes());
    }
}