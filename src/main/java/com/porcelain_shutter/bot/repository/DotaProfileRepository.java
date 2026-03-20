package com.porcelain_shutter.bot.repository;

import com.porcelain_shutter.bot.entity.DotaProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DotaProfileRepository extends JpaRepository<DotaProfile, Long> {

    List<DotaProfile> findAllByChatIdAndUserId(Long chatId, Long userId);

    Optional<DotaProfile> findByChatIdAndUserIdAndDotaNicknameIgnoreCase(Long chatId, Long userId, String dotaNickname);

    List<DotaProfile> findAllByChatId(Long chatId);
}
