package com.porcelain_shutter.bot.repository;

import com.porcelain_shutter.bot.entity.Vote;
import com.porcelain_shutter.bot.entity.VoteType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoteRepository extends JpaRepository<Vote, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vote v WHERE v.matchId = :matchId AND v.chatId = :chatId AND v.userId = :userId")
    Optional<Vote> findByMatchIdAndChatIdAndUserIdForUpdate(@Param("matchId") String matchId, @Param("chatId") Long chatId, @Param("userId") Long userId);

    long countByMatchIdAndChatIdAndVoteType(String matchId, Long chatId, VoteType voteType);

    List<Vote> findAllByMatchIdAndChatId(String matchId, Long chatId);
}