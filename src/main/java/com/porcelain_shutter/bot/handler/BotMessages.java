package com.porcelain_shutter.bot.handler;

import com.porcelain_shutter.bot.dto.Team;
import com.porcelain_shutter.bot.dto.ThroneGame;
import com.porcelain_shutter.bot.dto.VoteResult;
import com.porcelain_shutter.bot.dto.VoterStat;
import com.porcelain_shutter.bot.entity.DotaProfile;
import com.porcelain_shutter.bot.entity.UserStatistic;
import com.porcelain_shutter.bot.entity.VoteType;
import com.porcelain_shutter.bot.service.LocaleService;
import com.porcelain_shutter.bot.utils.MessageFormattingUtility;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class BotMessages {

    private static final String NL = "\n";
    private static final String DOUBLE_NL = "\n\n";
    private static final String MATCH_LINK = "<a href=\"https://stratz.com/matches/%s\">%s</a>";
    private static final int NUMBER_OF_POINTS_FOR_THRONE_ACTIVATION = 100;

    private final LocaleService localeService;

    public String pollStartMessage(String matchId, List<Team> teams, String predictionComment, String memeComment) {
        StringBuilder sb = new StringBuilder();
        sb.append(localeService.fmt("bot.poll.start.title", esc(matchId))).append(DOUBLE_NL);

        List<Team> ordered = teams.stream()
                .sorted((a, b) -> {
                    boolean aRadiant = VoteType.RADIANT.equalsToSideName(a.getTeamSide());
                    boolean bRadiant = VoteType.RADIANT.equalsToSideName(b.getTeamSide());
                    return Boolean.compare(aRadiant, bRadiant);
                })
                .toList();

        for (int i = 0; i < ordered.size(); i++) {
            Team team = ordered.get(i);
            String side = team.getTeamSide();
            String emoji = localeService.get(
                    VoteType.RADIANT.equalsToSideName(side)
                            ? "bot.green.apple.emoji"
                            : "bot.red.apple.emoji"
            );
            sb.append(localeService.fmt("bot.poll.start.team.header", emoji, esc(side))).append(NL);

            if (team.getPlayers() != null && !team.getPlayers().isEmpty()) {
                String players = team.getPlayers().stream()
                        .map(player -> localeService.fmt("bot.poll.start.team.player", esc(player.getNickname()), esc(player.getCharacter())))
                        .collect(Collectors.joining(" | "));
                sb.append(players).append(NL);
            }
            if (i < ordered.size() - 1) {
                sb.append(localeService.get("bot.poll.start.versus")).append(NL);
            }
        }
        sb.append(NL);

        if (predictionComment != null && !predictionComment.isBlank()) {
            sb.append(localeService.fmt("bot.poll.start.prediction_comment", esc(predictionComment))).append(NL);
        }
        if (memeComment != null && !memeComment.isBlank()) {
            sb.append(localeService.fmt("bot.poll.start.meme_comment", esc(memeComment)));
        }
        return sb.append(DOUBLE_NL).append(localeService.get("bot.poll.start.footer")).toString();
    }

    public String votingClosed(VoteResult results, String memeComment, boolean has322, List<String> playerSides) {
        StringBuilder sb = new StringBuilder();
        sb.append(localeService.get("bot.voting.closed.title")).append(DOUBLE_NL);
        sb.append(localeService.get("bot.voting.results_header")).append(NL);

        String direName = sideLabel(VoteType.DIRE.getSideName(), playerSides, false);
        String radiantName = sideLabel(VoteType.RADIANT.getSideName(), playerSides, false);

        sb.append(localeService.fmt("bot.match.summary.dire.line", localeService.get("bot.red.apple.emoji"), direName, results.dire(), results.direPercent())).append(NL);
        if (has322) {
            sb.append(localeService.fmt("bot.match.summary.dire.line", localeService.get("bot.green.apple.emoji"), radiantName, results.radiant(), results.radiantPercent())).append(NL);
            sb.append(localeService.get("bot.voting.closed.has322")).append(NL);
        } else {
            sb.append(localeService.fmt("bot.match.summary.radiant.line", localeService.get("bot.green.apple.emoji"), radiantName, results.radiant(), results.radiantPercent())).append(NL);
        }

        if (memeComment != null && !memeComment.isBlank()) {
            sb.append(NL).append(localeService.fmt("bot.voting.closed.meme_comment", esc(memeComment))).append(NL);
        }
        return sb.append(NL).append(localeService.get(
                results.totalVotes() != 0
                        ? "bot.voting.closed.footer"
                        : "bot.voting.closed.footer.zero.votes"
        )).toString();
    }

    public String matchSummary(VoteResult results, String teamWon, String matchId, ThroneGame throne, List<String> playerSides, boolean mmrEnabled) {
        VoteType wonVote = VoteType.findBySideName(teamWon);
        boolean radiantWon = VoteType.RADIANT == wonVote;
        String wonTeamName = sideLabel(wonVote.getSideName(), playerSides, true);
        String wonEmoji = localeService.get(radiantWon ? "bot.green.apple.emoji" : "bot.red.apple.emoji");

        String matchLink = String.format(MATCH_LINK, esc(matchId), localeService.pick("pool.match.headers.good"));

        String direLabel = sideLabel(VoteType.DIRE.getSideName(), playerSides, false);
        String radiantLabel = sideLabel(VoteType.RADIANT.getSideName(), playerSides, false);

        String direMmrTag = mmrEnabled ? (radiantWon ? localeService.get("bot.match.summary.lose.points") : localeService.get("bot.match.summary.win.points")) : Strings.EMPTY;
        String radiantMmrTag = mmrEnabled ? (radiantWon ? localeService.get("bot.match.summary.win.points") : localeService.get("bot.match.summary.lose.points")) : Strings.EMPTY;

        List<VoterStat> direVoters = radiantWon ? throne.loseVoters() : throne.winVoters();
        List<VoterStat> radiantVoters = radiantWon ? throne.winVoters() : throne.loseVoters();

        StringBuilder sb = new StringBuilder();
        sb.append(localeService.fmt("bot.match.summary.header", matchLink, wonEmoji, wonTeamName)).append(DOUBLE_NL);
        sb.append(localeService.get("bot.voting.results_header")).append(NL);

        // Dire
        sb.append(localeService.fmt("bot.match.summary.dire.line", localeService.get("bot.red.apple.emoji"), direLabel, results.dire(), results.direPercent()))
                .append(direMmrTag);
        if (!direVoters.isEmpty()) {
            sb.append(localeService.fmt("bot.match.summary.bonus", formatVoterList(direVoters, mmrEnabled)));
        }
        sb.append(NL);

        // Radiant
        sb.append(localeService.fmt("bot.match.summary.radiant.line", localeService.get("bot.green.apple.emoji"), radiantLabel, results.radiant(), results.radiantPercent()))
                .append(radiantMmrTag);
        if (!radiantVoters.isEmpty()) {
            sb.append(localeService.fmt("bot.match.summary.bonus", formatVoterList(radiantVoters, mmrEnabled)));
        }
        sb.append(DOUBLE_NL);

        boolean majorityCorrect = (results.radiant() >= results.dire() && radiantWon) || (results.dire() > results.radiant() && !radiantWon);

        if (throne.has322()) {
            sb.append(localeService.pick(throne.betrayalCorrect() ? "pool.crowd.322.correct" : "pool.crowd.322.wrong")).append(DOUBLE_NL);
        } else if (!throne.winVoters().isEmpty() || !throne.loseVoters().isEmpty()) {
            sb.append(localeService.pick(majorityCorrect ? "pool.crowd.no322.correct" : "pool.crowd.no322.wrong")).append(DOUBLE_NL);
        } else {
            sb.append(localeService.get("bot.voting.closed.footer.zero.votes")).append(DOUBLE_NL);
        }
        sb.append(throneBlock(throne));
        return sb.toString().stripTrailing();
    }

    private String throneBlock(ThroneGame throne) {
        return switch (throne.event()) {
            case THRONE_EMPTY -> localeService.get("bot.throne.empty") + NL;
            case NEW_KING -> {
                StringBuilder sb = new StringBuilder();
                sb.append(localeService.fmt(
                        throne.deposedKingName() != null
                                ? "bot.throne.new_king.deposed"
                                : "bot.throne.new_king.first",
                        esc(throne.kingName()), throne.kingMmr()
                )).append(NL);

                for (String challenger : throne.challengers()) {
                    sb.append(localeService.fmt("bot.throne.challenger", esc(challenger))).append(NL);
                }
                yield sb.toString();
            }
            case KING_DEFENDED -> {
                StringBuilder sb = new StringBuilder();
                sb.append(localeService.fmt("bot.throne.defended", esc(throne.kingName()), throne.kingMmr())).append(NL);

                for (String challenger : throne.challengers()) {
                    sb.append(localeService.fmt("bot.throne.challenger", esc(challenger))).append(NL);
                }
                yield sb.toString();
            }
            case BATTLE -> localeService.fmt("bot.throne.battle",
                    String.join(", ",
                            throne.battleContenders().stream()
                                    .map(BotMessages::esc)
                                    .toList()
                    )) + NL;
            case NONE -> Strings.EMPTY;
        };
    }

    private String sideLabel(String side, List<String> playerSides, boolean simple) {
        if (playerSides != null && playerSides.stream().anyMatch(playerSide -> playerSide.equalsIgnoreCase(side))) {
            return simple
                    ? esc(side)
                    : "<b><u>" + esc(side) + "</u></b>";
        }
        return esc(side);
    }

    private String formatVoterList(List<VoterStat> voters, boolean showMmr) {
        return voters.stream()
                .map(v -> showMmr
                        ? "<code>" + esc(v.displayName()) + "</code> (" + v.mmrAfter() + ")"
                        : "<code>" + esc(v.displayName()) + "</code>"
                )
                .collect(Collectors.joining(", "));
    }

    public String dotaUnregisterUsage(List<DotaProfile> nicknames) {
        if (!nicknames.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append(localeService.get("bot.dota.unregister_usage.header")).append(DOUBLE_NL);
            sb.append(localeService.get("bot.dota.unregister_usage.list")).append(NL);

            nicknames.forEach(profile -> sb.append(localeService.fmt("bot.dota.list_item", esc(profile.getDotaNickname()))).append(NL));
            return sb.toString().stripTrailing();
        }
        return dotaNotRegistered();
    }

    public String dotaMyProfile(String telegramDisplay, List<DotaProfile> nicknames) {
        if (!nicknames.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append(localeService.fmt("bot.dota.profile.header", esc(telegramDisplay))).append(NL);

            nicknames.forEach(profile -> sb.append(localeService.fmt("bot.dota.list_item", esc(profile.getDotaNickname()))).append(NL));
            return sb.toString().stripTrailing();
        }
        return dotaNotRegistered();
    }

    public String statisticNotFound(String callerUsername) {
        String displayName = callerUsername != null
                ? "@" + callerUsername
                : localeService.get("bot.stat.you");
        return localeService.fmt("bot.stat.not_found", displayName);
    }

    public String userStatisticCard(String header, UserStatistic userStatistic) {
        StringBuilder sb = new StringBuilder();
        sb.append(header).append(NL);

        String trendEmoji = localeService.get(
                userStatistic.getCorrectPredictions() >= userStatistic.getWrongPredictions()
                        ? "bot.upwards.trend.emoji"
                        : "bot.downwards.trend.emoji"
        );

        boolean hasBetrayal = userStatistic.totalBetrayals() > 0;
        int streak = userStatistic.getCurrentStreak();
        boolean hasStreak = Math.abs(streak) > 1;

        sb.append(localeService.fmt("bot.stat.card.mmr", userStatistic.getPredictorMmr())).append(NL);

        String forecastLine = localeService.fmt("bot.stat.card.predictions", trendEmoji, userStatistic.getCorrectPredictions(), userStatistic.getWrongPredictions());
        sb.append(hasBetrayal || hasStreak
                        ? localeService.get("bot.stat.tree.mid")
                        : localeService.get("bot.stat.tree.end"))
                .append(forecastLine).append(NL);

        if (hasBetrayal) {
            String betrayalLine = localeService.fmt("bot.stat.card.betrayal", userStatistic.getBetrayalCorrect(), userStatistic.getBetrayalWrong());
            sb.append(hasStreak
                            ? localeService.get("bot.stat.tree.mid")
                            : localeService.get("bot.stat.tree.end"))
                    .append(betrayalLine).append(NL);
        }

        if (hasStreak) {
            String streakEmoji = streak > 0
                    ? localeService.get("bot.stat.card.streak.win.emoji")
                    : localeService.get("bot.stat.card.streak.lose.emoji");
            String streakLabel = streak > 0
                    ? localeService.get("bot.stat.card.streak.win")
                    : localeService.get("bot.stat.card.streak.lose");
            sb.append(localeService.get("bot.stat.tree.end")).append(localeService.fmt("bot.stat.card.streak.line", streakEmoji, streakLabel, Math.abs(streak))).append(NL);
        }
        sb.append(lastPredictionLine(userStatistic));
        return sb.toString().stripTrailing();
    }

    private String lastPredictionLine(UserStatistic userStatistic) {
        if (userStatistic.getLastVoteType() == null || userStatistic.getLastVoteCorrect() == null) {
            return Strings.EMPTY;
        }

        String side = userStatistic.getLastVoteType().getSideName();
        String result = userStatistic.getLastVoteCorrect()
                ? localeService.get("bot.stat.result.ok")
                : localeService.get("bot.stat.result.fail");

        String suffix;
        if (Boolean.TRUE.equals(userStatistic.getLastVoteWasBetrayal())) {
            suffix = localeService.fmt("bot.stat.suffix.322", localeService.indexedPoolPick("pool.phrases.322", userStatistic.totalPredictions()));
        } else if (Boolean.TRUE.equals(userStatistic.getLastVoteInMatch())) {
            suffix = localeService.fmt("bot.stat.suffix.believer", localeService.indexedPoolPick("pool.phrases.believer", userStatistic.totalPredictions()));
        } else {
            suffix = localeService.fmt("bot.stat.suffix.watcher", localeService.indexedPoolPick("pool.phrases.watcher", userStatistic.totalPredictions()));
        }
        return localeService.fmt("bot.stat.last_prediction", side, result, suffix);
    }

    public String renderAllPlayersStatisticsTable(List<UserStatistic> userStatistics) {
        StringBuilder sb = new StringBuilder();
        sb.append(localeService.get("bot.stat.all.header")).append(DOUBLE_NL);

        boolean isThroneActive = !userStatistics.isEmpty() && userStatistics.getFirst().getPredictorMmr() >= NUMBER_OF_POINTS_FOR_THRONE_ACTIVATION;
        for (int i = 0; i < userStatistics.size(); i++) {
            UserStatistic userStatistic = userStatistics.get(i);
            boolean isKing = i == 0;
            boolean hasBetrayal = userStatistic.totalBetrayals() > 0;

            String name = MessageFormattingUtility.displayName(userStatistic.getUserId(), userStatistic.getUsername());
            String trendEmoji = localeService.get(
                    userStatistic.getCorrectPredictions() >= userStatistic.getWrongPredictions()
                            ? "bot.upwards.trend.emoji"
                            : "bot.downwards.trend.emoji"
            );

            if (isKing && isThroneActive) {
                sb.append(localeService.fmt("bot.stat.all.user.king", esc(name))).append(NL);
            } else {
                sb.append(localeService.fmt("bot.stat.all.user.default", localeService.pick("bot.animals.emoji"), esc(name))).append(NL);
            }
            sb.append(localeService.fmt("bot.stat.card.mmr", userStatistic.getPredictorMmr())).append(NL);

            String forecastLine = localeService.fmt("bot.stat.all.predictions", trendEmoji, userStatistic.getCorrectPredictions(), userStatistic.getWrongPredictions(), userStatistic.correctPercent());
            sb.append(hasBetrayal
                            ? localeService.get("bot.stat.tree.mid")
                            : localeService.get("bot.stat.tree.end"))
                    .append(forecastLine).append(NL);

            if (hasBetrayal) {
                long betrayalTotal = userStatistic.totalBetrayals();
                long betrayalPercent = betrayalTotal == 0 ? 0 : Math.round(100.0 * userStatistic.getBetrayalCorrect() / betrayalTotal);
                sb.append(localeService.get("bot.stat.tree.end"))
                        .append(localeService.fmt("bot.stat.all.betrayal", userStatistic.getBetrayalCorrect(), userStatistic.getBetrayalWrong(), betrayalPercent))
                        .append(NL);
            }
            sb.append(NL);
        }
        return sb.toString().stripTrailing();
    }

    private static String esc(String text) {
        if (text != null) {
            return text.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
        }
        return Strings.EMPTY;
    }

    // -----------------------------------------------------------------------
    // Simple messages
    // -----------------------------------------------------------------------

    public String about() {
        return localeService.get("bot.about.text");
    }

    public String voteAccepted(VoteType type) {
        return type == VoteType.RADIANT
                ? localeService.get("bot.vote.accepted.radiant")
                : localeService.get("bot.vote.accepted.dire");
    }

    public String voteAlreadyCast() {
        return localeService.get("bot.vote.already_cast");
    }

    public String votingAlreadyClosed() {
        return localeService.get("bot.vote.already_closed");
    }

    public String unknownAction() {
        return localeService.get("bot.vote.unknown_action");
    }

    public String chatResolutionError() {
        return localeService.get("bot.vote.chat_resolution_error");
    }

    public String voteForOwnTeam() {
        return localeService.get("bot.vote.own_team");
    }

    public String voteAgainstOwnTeam() {
        return localeService.get("bot.vote.against_team");
    }

    public String dotaRegistered(String dotaNickname) {
        return localeService.fmt("bot.dota.registered", esc(dotaNickname));
    }

    public String dotaAlreadyExists(String dotaNickname) {
        return localeService.fmt("bot.dota.exists", esc(dotaNickname));
    }

    public String dotaUnregistered(String dotaNickname) {
        return localeService.fmt("bot.dota.unregistered", esc(dotaNickname));
    }

    public String dotaNickNotFound(String dotaNickname) {
        return localeService.fmt("bot.dota.not_found", esc(dotaNickname));
    }

    public String dotaMissingNickname() {
        return localeService.get("bot.dota.missing_nickname");
    }

    public String statisticUserNotFound(String username) {
        return localeService.fmt("bot.stat.user_not_found", esc(username));
    }

    public String statisticAllEmpty() {
        return localeService.get("bot.stat.all_empty");
    }

    public String statisticHeaderByUsername(String username) {
        return localeService.fmt("bot.stat.header.user", esc(username));
    }

    public String statisticHeaderSelf() {
        return localeService.get("bot.stat.header.self");
    }

    private String dotaNotRegistered() {
        return localeService.get("bot.dota.none_registered");
    }
}
