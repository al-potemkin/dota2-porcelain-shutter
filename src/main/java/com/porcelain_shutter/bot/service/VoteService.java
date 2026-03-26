package com.porcelain_shutter.bot.service;

import com.porcelain_shutter.bot.dto.VoteResult;
import com.porcelain_shutter.bot.entity.Vote;
import com.porcelain_shutter.bot.entity.VoteType;
import com.porcelain_shutter.bot.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoteService {

    private final VoteRepository voteRepository;

    /**
     * Atomically casts a vote for a user in a chat/match.
     * <p>
     * Race condition protection — three layers:
     * 1. SERIALIZABLE transaction isolation
     * 2. PESSIMISTIC_WRITE row lock on SELECT
     * 3. DB UNIQUE constraint (match_id, chat_id, user_id) as last resort
     *
     * @return true if vote saved, false if user already voted
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public boolean castVote(Long chatId, Long userId, String username, VoteType voteType, String matchId) {
        try {
            Optional<Vote> existing = voteRepository.findByMatchIdAndChatIdAndUserIdForUpdate(matchId, chatId, userId);
            if (existing.isPresent()) {
                log.info("[VoteService] Duplicate vote: chatId={} userId={}", chatId, userId);
                return false;
            }

            voteRepository.save(Vote.builder()
                    .matchId(matchId)
                    .chatId(chatId)
                    .userId(userId)
                    .username(username)
                    .voteType(voteType)
                    .votedAt(LocalDateTime.now())
                    .build());

            log.info("[VoteService] Vote saved: matchId={} chatId={} userId={} type={}", matchId, chatId, userId, voteType);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.warn("[VoteService] Constraint violation (duplicate): chatId={} userId={}", chatId, userId);
            return false;
        }
    }

    @Transactional(readOnly = true)
    public VoteResult getResultsByMatchId(String matchId, Long chatId) {
        long radiant = voteRepository.countByMatchIdAndChatIdAndVoteType(matchId, chatId, VoteType.RADIANT);
        long dire = voteRepository.countByMatchIdAndChatIdAndVoteType(matchId, chatId, VoteType.DIRE);
        return new VoteResult(radiant, dire);
    }
}