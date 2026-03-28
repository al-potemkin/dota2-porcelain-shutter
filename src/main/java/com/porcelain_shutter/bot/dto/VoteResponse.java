package com.porcelain_shutter.bot.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class VoteResponse {
    private VoteStatus status;
    private String matchId;
    private String teamWon;
    private List<Long> processedChatIds;
    private List<Long> skippedChatIds;
    private String error;

    public enum VoteStatus {
        NO_RECIPIENTS, STARTED, WILL_NOT_BE_STARTED, ALREADY_LAUNCHED, SUMMARIZED, ALREADY_SUMMARIZED, NOT_EXIST
    }
}
