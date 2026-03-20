package com.porcelain_shutter.bot.repository;

import com.porcelain_shutter.bot.entity.UserStatistic;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserStatisticRepository extends JpaRepository<UserStatistic, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM UserStatistic s WHERE s.chatId = :chatId AND s.userId = :userId")
    Optional<UserStatistic> findByChatIdAndUserIdForUpdate(
            @Param("chatId") Long chatId,
            @Param("userId") Long userId
    );

    Optional<UserStatistic> findByChatIdAndUserId(Long chatId, Long userId);

    @Query("SELECT s FROM UserStatistic s WHERE s.chatId = :chatId AND LOWER(s.username) = LOWER(:username)")
    Optional<UserStatistic> findByChatIdAndUsernameCaseInsensitive(
            @Param("chatId") Long chatId,
            @Param("username") String username
    );

    @Query("SELECT s FROM UserStatistic s WHERE s.chatId = :chatId " +
            "ORDER BY s.predictorMmr DESC, s.correctPredictions DESC")
    List<UserStatistic> findByChatIdOrderByMmr(@Param("chatId") Long chatId);
}