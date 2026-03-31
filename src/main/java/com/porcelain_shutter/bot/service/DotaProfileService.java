package com.porcelain_shutter.bot.service;

import com.porcelain_shutter.bot.dto.RegisterResult;
import com.porcelain_shutter.bot.dto.Team;
import com.porcelain_shutter.bot.entity.DotaProfile;
import com.porcelain_shutter.bot.repository.DotaProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DotaProfileService {

    private final DotaProfileRepository repository;

    /**
     * Adds a new Dota 2 nickname to a user within the chat.
     * If the same nickname is already registered for this user, returns ALREADY_EXISTS.
     */
    @Transactional
    public RegisterResult register(Long chatId, Long userId, String telegramUsername, String dotaNickname) {
        Optional<DotaProfile> existing = repository
                .findByChatIdAndUserIdAndDotaNicknameIgnoreCase(chatId, userId, dotaNickname);

        if (existing.isPresent()) {
            log.info("[DotaProfile] Already exists chatId={} userId={} nickname='{}'", chatId, userId, dotaNickname);
            return RegisterResult.ALREADY_EXISTS;
        }

        repository.save(DotaProfile.builder()
                .chatId(chatId)
                .userId(userId)
                .telegramUsername(telegramUsername)
                .dotaNickname(dotaNickname)
                .registeredAt(LocalDateTime.now())
                .build());
        log.info("[DotaProfile] Created chatId={} userId={} nickname='{}'", chatId, userId, dotaNickname);
        return RegisterResult.CREATED;
    }

    /**
     * Deletes a specific user nickname.
     * Returns true if the nickname was found and deleted.
     */
    @Transactional
    public boolean unregister(Long chatId, Long userId, String dotaNickname) {
        Optional<DotaProfile> existing = repository
                .findByChatIdAndUserIdAndDotaNicknameIgnoreCase(chatId, userId, dotaNickname);

        if (existing.isEmpty()) {
            log.info("[DotaProfile] Not found for delete chatId={} userId={} nickname='{}'", chatId, userId, dotaNickname);
            return false;
        }

        repository.delete(existing.get());
        log.info("[DotaProfile] Deleted chatId={} userId={} nickname='{}'", chatId, userId, dotaNickname);
        return true;
    }

    /**
     * All user nicknames in the chat.
     */
    @Transactional(readOnly = true)
    public List<DotaProfile> findAllByUser(Long chatId, Long userId) {
        return repository.findAllByChatIdAndUserId(chatId, userId);
    }

    /**
     * Checks if the user's nickname matches any of the nicknames in the match.
     * Used during voting to determine the "home" team.
     *
     * @param userNicknames  nicknames registered by the user
     * @param matchNicknames all nicknames of the match participants (from MatchStartRequest.players)
     * @return the first matching nickname or empty
     */
    public Optional<String> findMatchingNickname(List<String> userNicknames, Set<String> matchNicknames) {
        return userNicknames.stream()
                .filter(nickname -> matchNicknames.stream().anyMatch(matchNickname -> matchNickname.equalsIgnoreCase(nickname)))
                .findFirst();
    }

    /**
     * Returns a Set of nicknames of all match participants for quick search.
     */
    public Set<String> extractMatchNicknames(List<Team> teams) {
        return teams.stream()
                .flatMap(team -> team.getPlayers().stream())
                .map(Team.Player::getNickname)
                .collect(Collectors.toSet());
    }

    /**
     * Returns the teamSide of a player in a match based on his nickname.
     */
    public Optional<String> findPlayerTeam(String playerNickname, List<Team> teams) {
        return teams.stream()
                .filter(team -> team.getPlayers().stream()
                        .map(Team.Player::getNickname)
                        .anyMatch(nickname -> nickname.equalsIgnoreCase(playerNickname)))
                .map(Team::getTeamSide)
                .findFirst();
    }
}