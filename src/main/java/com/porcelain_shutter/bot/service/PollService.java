package com.porcelain_shutter.bot.service;

import com.porcelain_shutter.bot.entity.MatchStatus;
import com.porcelain_shutter.bot.entity.PollSession;
import com.porcelain_shutter.bot.repository.PollSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PollService {

    @Value("${telegram.bot.vote-duration-minutes:1}")
    private int voteDurationMinutes;

    private final PollSessionRepository pollSessionRepository;

    @Transactional
    public PollSession createSession(Long chatId, Integer messageId, String matchId,
                                     boolean hasPhoto, String teamsJson, String memeComment) {
        pollSessionRepository.findByChatIdAndMatchStatus(chatId, MatchStatus.ACTIVE) // Close any dangling ACTIVE sessions for this chat
                .ifPresent(stale -> {
                    stale.setMatchStatus(MatchStatus.VOTING_CLOSED);
                    pollSessionRepository.save(stale);
                    log.warn("[PollService] Closed stale ACTIVE session id={} matchId={}", stale.getId(), stale.getMatchId());
                });

        LocalDateTime now = LocalDateTime.now();
        PollSession session = PollSession.builder()
                .matchId(matchId)
                .chatId(chatId)
                .messageId(messageId)
                .hasPhoto(hasPhoto)
                .teamsJson(teamsJson)
                .memeComment(memeComment)
                .startedAt(now)
                .endsAt(now.plusMinutes(voteDurationMinutes))
                .matchStatus(MatchStatus.ACTIVE)
                .build();

        PollSession saved = pollSessionRepository.save(session);
        log.info("[PollService] Session created: id={} matchId={} chatId={} hasPhoto={} endsAt={}",
                saved.getId(), matchId, chatId, hasPhoto, saved.getEndsAt());
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<PollSession> getActiveSession(Long chatId) {
        return pollSessionRepository.findByChatIdAndMatchStatus(chatId, MatchStatus.ACTIVE);
    }

    /**
     * Finds a session by messageId - precise search when processing the callback button.
     */
    public Optional<PollSession> getSessionByMessageId(Long chatId, Integer messageId) {
        return pollSessionRepository.findByChatIdAndMessageId(chatId, messageId);
    }

    @Transactional(readOnly = true)
    public List<PollSession> findAllByMatchId(String matchId) {
        return pollSessionRepository.findAllByMatchId(matchId);
    }

    /**
     * Finds ACTIVE sessions whose voting window has expired — called by scheduler.
     */
    @Transactional(readOnly = true)
    public List<PollSession> findExpiredSessions() {
        return pollSessionRepository.findAllByMatchStatusAndEndsAtBefore(MatchStatus.ACTIVE, LocalDateTime.now());
    }

    /**
     * Voting window closed — results posted, waiting for match outcome.
     */
    @Transactional
    public void markVotingClosed(PollSession session) {
        session.setMatchStatus(MatchStatus.VOTING_CLOSED);
        pollSessionRepository.save(session);
        log.info("[PollService] Session id={} matchId={} → VOTING_CLOSED",
                session.getId(), session.getMatchId());
    }

    /**
     * Match outcome received — summary posted.
     */
    @Transactional
    public void markSummarized(PollSession session) {
        session.setMatchStatus(MatchStatus.SUMMARIZED);
        pollSessionRepository.save(session);
        log.info("[PollService] Session id={} matchId={} → SUMMARIZED",
                session.getId(), session.getMatchId());
    }
}