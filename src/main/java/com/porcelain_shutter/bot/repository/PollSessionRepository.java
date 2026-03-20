package com.porcelain_shutter.bot.repository;

import com.porcelain_shutter.bot.entity.MatchStatus;
import com.porcelain_shutter.bot.entity.PollSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PollSessionRepository extends JpaRepository<PollSession, Long> {

    Optional<PollSession> findByChatIdAndMatchStatus(Long chatId, MatchStatus status);

    Optional<PollSession> findByChatIdAndMessageId(Long chatId, Integer messageId);

    List<PollSession> findAllByMatchId(String matchId);

    List<PollSession> findAllByMatchStatusAndEndsAtBefore(MatchStatus status, LocalDateTime now);
}