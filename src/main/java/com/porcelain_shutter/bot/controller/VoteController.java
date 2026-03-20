package com.porcelain_shutter.bot.controller;

import com.porcelain_shutter.bot.dto.MatchEndRequest;
import com.porcelain_shutter.bot.dto.MatchStartRequest;
import com.porcelain_shutter.bot.handler.BotMessages;
import com.porcelain_shutter.bot.handler.MatchVotingBot;
import com.porcelain_shutter.bot.service.BannerProcessingService;
import com.porcelain_shutter.bot.service.RegisteredChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST API for controlling the voting lifecycle.
 * <p>
 * POST /api/vote/start      — Step 1: publish poll in the group chat
 * POST /api/vote/summarize  — Step 3: post match outcome after the real game ends
 * <p>
 * Step 2 (closing voting + showing percentages) happens automatically after 1 minute via the scheduler inside VotingBot.
 */
@Slf4j
@RestController
@RequestMapping("/api/vote")
@RequiredArgsConstructor
public class VoteController {

    private final BannerProcessingService bannerProcessingService;
    private final RegisteredChatService registeredChatService;
    private final MatchVotingBot matchVotingBot;
    private final BotMessages botMessages;

    // -----------------------------------------------------------------------
    // Step 1 — start voting
    // -----------------------------------------------------------------------

    /**
     * Publishes a voting poll to the Telegram group.
     * <p>
     * Example request body:
     * <pre>
     * {
     *   "matchId": "match-001",
     *   "predictionComment": "Radiant має кращий пізній гейм на цьому патчі",
     *   "memeComment": "Обирай мудро або страждай 😤",
     *   "teams": [
     *     { "teamSide": "Radiant", "players": [
     *         { "nickname": "Player 0", "character": "Axe" }, ...
     *     ]},
     *     { "teamSide": "Dire", "players": [...] }
     *   ]
     * }
     * </pre>
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startVote(@RequestBody @Valid MatchStartRequest matchStart) {
        log.info("[VoteController] POST /api/vote/start matchId={}", matchStart.getMatchId());

        List<Long> chatIds = registeredChatService.getActiveChatIds();
        if (chatIds.isEmpty()) {
            log.warn("[VoteController] No active chats registered — bot not added to any group");
            return ResponseEntity
                    .status(409)
                    .body(Map.of("error", "Bot is not added to any group chat"));
        }

        byte[] imageBytes = bannerProcessingService.getBannerImageBytes(matchStart.getTeams());
        String voteText = botMessages.pollStartMessage(
                matchStart.getMatchId(), matchStart.getTeams(),
                matchStart.getPredictionComment(), matchStart.getMemeComment());
        log.debug("[VoteController] Vote message built:\n{}", voteText);

        List<Long> started = new ArrayList<>();
        List<Long> skipped = new ArrayList<>();
        chatIds.forEach(
                chatId -> {
                    boolean ok = matchVotingBot.startVote(chatId, voteText, matchStart.getMatchId(), imageBytes, matchStart.getTeams(), matchStart.getMemeComment());
                    (ok ? started : skipped).add(chatId);
                }
        );
        log.info("[VoteController] Vote started. matchId={} started={} skipped={}",
                matchStart.getMatchId(), started, skipped);
        if (started.isEmpty()) {
            return ResponseEntity
                    .status(409)
                    .body(Map.of(
                            "error", "A vote is already in progress in all chats",
                            "skipped", skipped
                    ));
        }

        return ResponseEntity.ok(Map.of(
                "status", "started",
                "matchId", matchStart.getMatchId(),
                "started", started,
                "skipped", skipped
        ));
    }

    // -----------------------------------------------------------------------
    // Step 3 — post match outcome
    // -----------------------------------------------------------------------

    /**
     * Posts the final match result to the Telegram group.
     * Must be called AFTER the voting window has closed (or it will be force-closed).
     * <p>
     * Example request body:
     * <pre>
     * {
     *   "matchId": "match-001",
     *   "teamWon": "Radiant"
     * }
     * </pre>
     */
    @PostMapping("/summarize")
    public ResponseEntity<Map<String, Object>> summarizeVote(@RequestBody @Valid MatchEndRequest matchEnd) {
        log.info("[VoteController] POST /api/vote/summarize matchId={} teamWon={}", matchEnd.getMatchId(), matchEnd.getTeamWon());

        boolean summarized = matchVotingBot.summarizeVote(matchEnd.getMatchId(), matchEnd.getTeamWon());
        if (!summarized) {
            log.warn("[VoteController] Could not summarize — session not found or already summarized. matchId={}", matchEnd.getMatchId());
            return ResponseEntity
                    .status(404)
                    .body(Map.of(
                            "error", "No eligible session found for matchId: " + matchEnd.getMatchId(),
                            "hint", "Session may not exist, or has already been summarized"
                    ));
        }
        log.info("[VoteController] Summary posted. matchId={} teamWon={}", matchEnd.getMatchId(), matchEnd.getTeamWon());
        return ResponseEntity.ok(Map.of(
                "status", "summarized",
                "matchId", matchEnd.getMatchId(),
                "teamWon", matchEnd.getTeamWon()
        ));
    }
}