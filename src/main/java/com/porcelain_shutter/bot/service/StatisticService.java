package com.porcelain_shutter.bot.service;

import com.porcelain_shutter.bot.dto.Team;
import com.porcelain_shutter.bot.dto.ThroneEvent;
import com.porcelain_shutter.bot.dto.ThroneGame;
import com.porcelain_shutter.bot.dto.VoterStat;
import com.porcelain_shutter.bot.entity.DotaProfile;
import com.porcelain_shutter.bot.entity.UserStatistic;
import com.porcelain_shutter.bot.entity.Vote;
import com.porcelain_shutter.bot.entity.VoteType;
import com.porcelain_shutter.bot.repository.DotaProfileRepository;
import com.porcelain_shutter.bot.repository.UserStatisticRepository;
import com.porcelain_shutter.bot.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticService {

    public static final String USER = "User#";

    private static final long MMR_WIN = 25L;
    private static final long MMR_LOSS = 15L;
    private static final long MMR_BOUNTY = 15L;
    private static final long THRONE_THRESHOLD = 100L;
    private static final int MIN_VOTERS_FOR_MMR = 3; // Minimum number of votes in a match to add/remove MMR

    private final UserStatisticRepository statisticRepository;
    private final DotaProfileRepository dotaProfileRepository;
    private final VoteRepository voteRepository;
    private final ObjectMapperConverter objectMapperConverter;

    // -----------------------------------------------------------------------
    // Post-match update
    // -----------------------------------------------------------------------

    /**
     * Called after summarizeVote.
     * Updates MMR, streaks, and betrayal counters for all voters.
     * Calculates the throne state and returns ThroneResult for generating a message.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ThroneGame updateAfterMatch(String matchId, Long chatId, String teamWon, String teamsJson) {
        log.info("[StatisticService] updateAfterMatch matchId={} chatId={} teamWon={}", matchId, chatId, teamWon);
        List<Vote> votes = voteRepository.findAllByMatchIdAndChatId(matchId, chatId);
        List<Team> teams = objectMapperConverter.deserializeTeams(teamsJson);
        Map<Long, List<String>> nicknames = loadChatNicksByUser(chatId);

        Map<Long, Long> mmrBefore = snapshotMMR(chatId);
        String oldKing = resolveKingName(mmrBefore, chatId);

        VoteType winningVote = VoteType.findBySideName(teamWon);
        boolean mmrEnabled = votes.size() >= MIN_VOTERS_FOR_MMR;
        boolean anyBetrayal = false;
        boolean betrayalWasRight = false;

        for (Vote vote : votes) {
            boolean correct = vote.getVoteType() == winningVote;
            boolean isBetrayal = detectBetrayal(vote.getUserId(), vote.getVoteType(), teams, nicknames);

            updateUserStatistics(chatId, vote.getUserId(), vote.getVoteType(), correct, isBetrayal,
                    vote.getUsername(), teams, nicknames, mmrEnabled);

            if (isBetrayal) {
                anyBetrayal = true;
                if (correct) betrayalWasRight = true;
            }
        }

        // Throne: define event and bounty
        Map<Long, Long> mmrAfter = snapshotMMR(chatId);
        ThroneEvent event = resolveThroneEvent(mmrBefore, mmrAfter);
        String newKing = resolveKingName(mmrAfter, chatId);
        long kingMmr = resolveKingMmr(mmrAfter);

        if (newKing != null && event == ThroneEvent.NEW_KING) {
            awardBounty(chatId, mmrAfter);
            mmrAfter = snapshotMMR(chatId);
            kingMmr = resolveKingMmr(mmrAfter);
            newKing = resolveKingName(mmrAfter, chatId);
            log.info("[StatisticService] Bounty +{} awarded to '{}'", MMR_BOUNTY, newKing);
        }

        List<String> challengers = resolveChallengers(mmrAfter, kingMmr, newKing, chatId);
        List<String> battle = event == ThroneEvent.BATTLE
                ? resolveBattleContenders(mmrAfter, chatId)
                : Collections.emptyList();

        List<VoterStat> winVoters = buildVoterStats(votes, winningVote, true, mmrAfter);
        List<VoterStat> loseVoters = buildVoterStats(votes, winningVote, false, mmrAfter);

        log.info("[StatisticService] Throne event={} king='{}' mmr={}", event, newKing, kingMmr);

        return new ThroneGame(
                winVoters, loseVoters,
                event, newKing, kingMmr,
                battle, challengers,
                oldKing,
                anyBetrayal, betrayalWasRight
        );
    }

    @Transactional(readOnly = true)
    public Optional<UserStatistic> getByUserId(Long chatId, Long userId) {
        return statisticRepository.findByChatIdAndUserId(chatId, userId);
    }

    @Transactional(readOnly = true)
    public Optional<UserStatistic> getByUsername(Long chatId, String username) {
        return statisticRepository.findByChatIdAndUsernameCaseInsensitive(chatId, username);
    }

    @Transactional(readOnly = true)
    public List<UserStatistic> getChatStatistics(Long chatId) {
        return statisticRepository.findByChatIdOrderByMmr(chatId);
    }

    /**
     * Is there at least one registered player in this match who has bet against their team ?
     */
    @Transactional(readOnly = true)
    public boolean hasBetrayal(String matchId, Long chatId, String teamsJson) {
        List<Vote> votes = voteRepository.findAllByMatchIdAndChatId(matchId, chatId);
        List<Team> teams = objectMapperConverter.deserializeTeams(teamsJson);
        if (teams == null || teams.isEmpty()) return false;
        Map<Long, List<String>> nicknames = loadChatNicksByUser(chatId);
        return votes.stream().anyMatch(v -> detectBetrayal(v.getUserId(), v.getVoteType(), teams, nicknames));
    }

    /**
     * Returns the match sides (Radiant/Dire) that have registered chat players.
     * Used to underline teams in messages.
     */
    @Transactional(readOnly = true)
    public Set<String> findPlayerSides(Long chatId, String teamsJson) {
        List<Team> teams = objectMapperConverter.deserializeTeams(teamsJson);
        if (teams == null || teams.isEmpty()) return Set.of();

        Set<String> allNicks = loadChatNicksByUser(chatId).values().stream()
                .flatMap(Collection::stream)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        Set<String> sides = new HashSet<>();
        for (Team team : teams) {
            if (team.getPlayers() == null) continue;
            boolean hasPlayer = team.getPlayers().stream()
                    .anyMatch(p -> allNicks.contains(p.getNickname().toLowerCase()));
            if (hasPlayer) sides.add(team.getTeamSide());
        }
        return sides;
    }

    // -----------------------------------------------------------------------
    // Betrayal detection
    // -----------------------------------------------------------------------

    boolean detectBetrayal(Long userId, VoteType usersVote, List<Team> teams,
                           Map<Long, List<String>> profilesByUser) {
        if (teams == null || teams.isEmpty() || !profilesByUser.containsKey(userId)) {
            return false;
        }

        List<String> userNicknames = profilesByUser.getOrDefault(userId, Collections.emptyList());
        if (userNicknames.isEmpty()) {
            return false;
        }

        for (Team team : teams) {
            if (team.getPlayers() != null) {
                for (Team.Player player : team.getPlayers()) {
                    boolean nickMatches = userNicknames.stream()
                            .anyMatch(nickname -> nickname.equalsIgnoreCase(player.getNickname()));
                    if (nickMatches) {
                        boolean votedForSelf =
                                (VoteType.RADIANT.equalsToSideName(team.getTeamSide()) && usersVote == VoteType.RADIANT)
                                        || (VoteType.DIRE.equalsToSideName(team.getTeamSide()) && usersVote == VoteType.DIRE);
                        return !votedForSelf;
                    }
                }
            }
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Updating user statistics
    // -----------------------------------------------------------------------

    private void updateUserStatistics(Long chatId, Long userId, VoteType voteType,
                                      boolean correct, boolean isBetrayal,
                                      String username, List<Team> teams,
                                      Map<Long, List<String>> nicknames, boolean mmrEnabled) {

        UserStatistic stat = statisticRepository
                .findByChatIdAndUserIdForUpdate(chatId, userId)
                .orElseGet(() -> UserStatistic.builder()
                        .chatId(chatId).userId(userId)
                        .correctPredictions(0L).wrongPredictions(0L)
                        .winVotes(0L).loseVotes(0L)
                        .predictorMmr(0L)
                        .betrayalCorrect(0L).betrayalWrong(0L)
                        .currentStreak(0)
                        .build());

        if (username != null && !username.isBlank()) stat.setUsername(username);

        if (correct) stat.setCorrectPredictions(stat.getCorrectPredictions() + 1);
        else stat.setWrongPredictions(stat.getWrongPredictions() + 1);

        if (voteType == VoteType.RADIANT) stat.setWinVotes(stat.getWinVotes() + 1);
        else stat.setLoseVotes(stat.getLoseVotes() + 1);

        if (mmrEnabled) {
            long newMmr = correct
                    ? stat.getPredictorMmr() + MMR_WIN
                    : Math.max(0L, stat.getPredictorMmr() - MMR_LOSS);
            stat.setPredictorMmr(newMmr);

            if (isBetrayal) {
                if (correct) stat.setBetrayalCorrect(stat.getBetrayalCorrect() + 1);
                else stat.setBetrayalWrong(stat.getBetrayalWrong() + 1);
            }

            stat.setCurrentStreak(calcStreak(stat.getCurrentStreak(), correct));
        }

        stat.setLastVoteType(voteType);
        stat.setLastVoteCorrect(correct);
        stat.setLastVoteInMatch(isPlayerInMatch(userId, teams, nicknames));
        stat.setLastVoteWasBetrayal(isBetrayal);

        statisticRepository.save(stat);
        log.debug("[Stat] userId={} correct={} mmr={} mmrEnabled={} streak={} betrayal={}",
                userId, correct, stat.getPredictorMmr(), mmrEnabled, stat.getCurrentStreak(), isBetrayal);
    }

    private int calcStreak(int current, boolean correct) {
        if (correct) return current >= 0 ? current + 1 : 1;
        else return current <= 0 ? current - 1 : -1;
    }

    // -----------------------------------------------------------------------
    // Throne logic
    // -----------------------------------------------------------------------

    private ThroneEvent resolveThroneEvent(Map<Long, Long> mmrBefore,
                                           Map<Long, Long> mmrAfter) {
        long maxAfter = mmrAfter.values().stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L);
        if (maxAfter < THRONE_THRESHOLD) {
            return ThroneEvent.THRONE_EMPTY;
        }

        long countAfter = mmrAfter.values().stream()
                .filter(max -> max == maxAfter)
                .count();
        if (countAfter > 1) {
            return ThroneEvent.BATTLE;
        }

        long maxBefore = mmrBefore.values().stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L);

        long countBefore = mmrBefore.values().stream()
                .filter(max -> max == maxBefore)
                .count();
        Long prevKingId = mmrBefore.entrySet().stream()
                .filter(entry -> entry.getValue() == maxBefore)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        Long newKingId = mmrAfter.entrySet().stream()
                .filter(entry -> entry.getValue() == maxAfter)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        boolean kingChanged = !Objects.equals(prevKingId, newKingId) || countBefore > 1;
        return kingChanged || maxBefore < THRONE_THRESHOLD
                ? ThroneEvent.NEW_KING
                : ThroneEvent.KING_DEFENDED;
    }

    private void awardBounty(Long chatId, Map<Long, Long> mmrAfter) {
        long max = mmrAfter.values().stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L);

        mmrAfter.entrySet().stream()
                .filter(entry -> entry.getValue() == max)
                .map(Map.Entry::getKey)
                .findFirst()
                .flatMap(kingId -> statisticRepository.findByChatIdAndUserIdForUpdate(chatId, kingId))
                .ifPresent(userStatistic -> {
                    userStatistic.setPredictorMmr(userStatistic.getPredictorMmr() + MMR_BOUNTY);
                    statisticRepository.save(userStatistic);
                });
    }

    private Map<Long, Long> snapshotMMR(Long chatId) {
        return statisticRepository.findByChatIdOrderByMmr(chatId).stream()
                .collect(Collectors.toMap(UserStatistic::getUserId, UserStatistic::getPredictorMmr));
    }

    private String resolveKingName(Map<Long, Long> mmr, Long chatId) {
        if (!mmr.isEmpty()) {
            long max = mmr.values().stream().mapToLong(Long::longValue).max().orElse(0L);
            if (max < THRONE_THRESHOLD) {
                return null;
            }

            long count = mmr.values().stream().filter(m -> m == max).count();
            if (count > 1) {
                return null;
            }

            Long kingId = mmr.entrySet().stream()
                    .filter(entry -> entry.getValue() == max)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
            if (kingId != null) {
                return statisticRepository.findByChatIdAndUserId(chatId, kingId)
                        .map(userService -> buildUsername(userService.getUsername(), userService.getUserId()))
                        .orElse(null);
            }
        }
        return null;
    }

    private long resolveKingMmr(Map<Long, Long> mmr) {
        return mmr.values().stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L);
    }

    private List<String> resolveChallengers(Map<Long, Long> mmr, long kingMmr, String kingName, Long chatId) {
        if (kingName == null || kingMmr < THRONE_THRESHOLD) {
            return Collections.emptyList();
        }
        return mmr.entrySet().stream()
                .filter(entry -> {
                    long diff = kingMmr - entry.getValue();
                    return diff > 0 && diff <= MMR_WIN;
                })
                .map(entry -> statisticRepository.findByChatIdAndUserId(chatId, entry.getKey())
                        .map(userService -> buildUsername(userService.getUsername(), userService.getUserId()))
                        .orElse(USER + entry.getKey()))
                .toList();
    }

    private List<String> resolveBattleContenders(Map<Long, Long> mmr, Long chatId) {
        long max = mmr.values().stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L);
        if (max < THRONE_THRESHOLD) {
            return Collections.emptyList();
        }
        return mmr.entrySet().stream()
                .filter(entry -> entry.getValue() == max)
                .map(entry -> statisticRepository.findByChatIdAndUserId(chatId, entry.getKey())
                        .map(userService -> buildUsername(userService.getUsername(), userService.getUserId()))
                        .orElse(USER + entry.getKey()))
                .toList();
    }

    private static String buildUsername(String username, Long userId) {
        return username != null && !username.isBlank()
                ? username
                : USER + userId;
    }

    private List<VoterStat> buildVoterStats(List<Vote> votes, VoteType winningVote,
                                            boolean forWinners, Map<Long, Long> mmrAfter) {
        return votes.stream()
                .filter(vote -> forWinners == (vote.getVoteType() == winningVote))
                .map(vote -> {
                    String name = buildUsername(vote.getUsername(), vote.getUserId());
                    long mmr = mmrAfter.getOrDefault(vote.getUserId(), 0L);
                    return new VoterStat(name, mmr, vote.getVoteType());
                })
                .sorted(Comparator.comparingLong(VoterStat::mmrAfter).reversed())
                .collect(Collectors.toList());
    }

    private boolean isPlayerInMatch(Long userId, List<Team> teams, Map<Long, List<String>> nicknames) {
        if (teams == null || teams.isEmpty()) {
            return false;
        }
        List<String> userNicknames = nicknames.getOrDefault(userId, Collections.emptyList());
        if (userNicknames.isEmpty()) {
            return false;
        }
        return teams.stream()
                .filter(team -> team.getPlayers() != null)
                .flatMap(team -> team.getPlayers().stream())
                .anyMatch(player -> userNicknames.stream().anyMatch(nickname -> nickname.equalsIgnoreCase(player.getNickname())));
    }

    private Map<Long, List<String>> loadChatNicksByUser(Long chatId) {
        return dotaProfileRepository.findAllByChatId(chatId).stream()
                .collect(Collectors.groupingBy(
                        DotaProfile::getUserId,
                        Collectors.mapping(DotaProfile::getDotaNickname, Collectors.toList())
                ));
    }
}