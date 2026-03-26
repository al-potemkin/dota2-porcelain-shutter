package com.porcelain_shutter.bot.dto;

public record VoteResult(long radiant, long dire) {
    public long totalVotes() {
        return radiant + dire;
    }

    public long radiantPercent() {
        return percent(radiant);
    }

    public long direPercent() {
        return percent(dire);
    }

    private long percent(long value) {
        return totalVotes() == 0 ? 0 : Math.round(100.0 * value / totalVotes());
    }
}