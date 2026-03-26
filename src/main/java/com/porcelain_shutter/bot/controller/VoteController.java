package com.porcelain_shutter.bot.controller;

import com.porcelain_shutter.bot.dto.MatchEndRequest;
import com.porcelain_shutter.bot.dto.MatchStartRequest;
import com.porcelain_shutter.bot.dto.Team;
import com.porcelain_shutter.bot.dto.VoteResponse;
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
    private static final String UNKNOWN = "unknown";

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
    public ResponseEntity<VoteResponse> startVote(@RequestBody @Valid MatchStartRequest matchStart) {
        log.info("[VoteController] POST /api/vote/start matchId={}", matchStart.getMatchId());

        List<Long> chatIds = registeredChatService.getActiveChatIds();
        if (chatIds.isEmpty()) {
            log.warn("[VoteController] No active chats registered — bot not added to any group");
            return ResponseEntity
                    .status(409)
                    .body(VoteResponse.builder()
                            .status(VoteResponse.VoteStatus.NO_RECIPIENTS)
                            .error("Bot is not added to any group chat")
                            .build());
        }

        long numberOfInvalidNames = matchStart.getTeams().stream()
                .flatMap(team -> team.getPlayers().stream())
                .map(Team.Player::getCharacter)
                .filter(character -> character == null || UNKNOWN.equalsIgnoreCase(character))
                .count();

        if (numberOfInvalidNames != 0) {
            return ResponseEntity
                    .status(400)
                    .body(VoteResponse.builder()
                            .status(VoteResponse.VoteStatus.WILL_NOT_BE_STARTED)
                            .matchId(matchStart.getMatchId())
                            .error("Unable to recognize names of characters participating in match")
                            .build());
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
                    .body(VoteResponse.builder()
                            .status(VoteResponse.VoteStatus.ALREADY_LAUNCHED)
                            .error("A vote is already in progress in all chats")
                            .matchId(matchStart.getMatchId())
                            .skippedChatIds(skipped)
                            .build());
        }

        return ResponseEntity.ok(
                VoteResponse.builder()
                        .status(VoteResponse.VoteStatus.STARTED)
                        .matchId(matchStart.getMatchId())
                        .startedChatIds(started)
                        .skippedChatIds(skipped)
                        .build()
        );
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
    public ResponseEntity<VoteResponse> summarizeVote(@RequestBody @Valid MatchEndRequest matchEnd) {
        log.info("[VoteController] POST /api/vote/summarize matchId={} teamWon={}", matchEnd.getMatchId(), matchEnd.getTeamWon());

        boolean summarized = matchVotingBot.summarizeVote(matchEnd.getMatchId(), matchEnd.getTeamWon());
        if (!summarized) {
            log.warn("[VoteController] Could not summarize — session not found or already summarized. matchId={}", matchEnd.getMatchId());
            return ResponseEntity
                    .status(404)
                    .body(VoteResponse.builder()
                            .status(VoteResponse.VoteStatus.NOT_EXIST)
                            .error("No eligible session found for matchId: " + matchEnd.getMatchId())
                            .build());
        }
        log.info("[VoteController] Summary posted. matchId={} teamWon={}", matchEnd.getMatchId(), matchEnd.getTeamWon());
        return ResponseEntity.ok(
                VoteResponse.builder()
                        .status(VoteResponse.VoteStatus.SUMMARIZED)
                        .matchId(matchEnd.getMatchId())
                        .teamWon(matchEnd.getTeamWon())
                        .build()
        );
    }
}