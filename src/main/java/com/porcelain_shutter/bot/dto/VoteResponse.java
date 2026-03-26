package com.porcelain_shutter.bot.dto;

import lombok.Builder;

import java.util.List;

@Builder
public class VoteResponse {
    private VoteStatus status;
    private String matchId;
    private String teamWon;
    private List<Long> startedChatIds;
    private List<Long> skippedChatIds;
    private String error;

    public enum VoteStatus {
        NO_RECIPIENTS, STARTED, WILL_NOT_BE_STARTED, ALREADY_LAUNCHED, SUMMARIZED, NOT_EXIST
    }
}
