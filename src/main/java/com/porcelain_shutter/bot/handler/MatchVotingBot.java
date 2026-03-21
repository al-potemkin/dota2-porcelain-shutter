package com.porcelain_shutter.bot.handler;

import com.porcelain_shutter.bot.config.BotCommands;
import com.porcelain_shutter.bot.config.KeyboardFactory;
import com.porcelain_shutter.bot.dto.RegisterResult;
import com.porcelain_shutter.bot.dto.Team;
import com.porcelain_shutter.bot.dto.ThroneGame;
import com.porcelain_shutter.bot.dto.VoteResult;
import com.porcelain_shutter.bot.entity.DotaProfile;
import com.porcelain_shutter.bot.entity.MatchStatus;
import com.porcelain_shutter.bot.entity.PollSession;
import com.porcelain_shutter.bot.entity.UserStatistic;
import com.porcelain_shutter.bot.entity.VoteType;
import com.porcelain_shutter.bot.service.DotaProfileService;
import com.porcelain_shutter.bot.service.ObjectMapperConverter;
import com.porcelain_shutter.bot.service.PollService;
import com.porcelain_shutter.bot.service.RegisteredChatService;
import com.porcelain_shutter.bot.service.StatisticService;
import com.porcelain_shutter.bot.service.VoteService;
import com.porcelain_shutter.bot.utils.MessageFormattingUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.LinkPreviewOptions;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberUpdated;
import org.telegram.telegrambots.meta.api.objects.message.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchVotingBot implements LongPollingSingleThreadUpdateConsumer {

    private static final String PARSE_MODE = "HTML";
    private static final int PLAYER_NUMBER_THRESHOLD = 3;
    public static final String IMAGE_FILENAME = "banner.jpg";

    private final TelegramClient telegramClient;
    private final PollService pollService;
    private final VoteService voteService;
    private final StatisticService statisticService;
    private final RegisteredChatService registeredChatService;
    private final DotaProfileService dotaProfileService;
    private final KeyboardFactory keyboardFactory;
    private final BotMessages botMessages;
    private final ObjectMapperConverter objectMapperConverter;

    // -----------------------------------------------------------------------
    // LongPollingSingleThreadUpdateConsumer
    // -----------------------------------------------------------------------

    @Override
    public void consume(Update update) {
        log.info("[Update] *** RECEIVED *** updateId={} hasCallback={} hasMessage={}",
                update.getUpdateId(), update.hasCallbackQuery(), update.hasMessage());

        if (update.hasMyChatMember()) {
            handleMyChatMember(update.getMyChatMember());
        } else if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
        } else if (update.hasMessage() && update.getMessage().hasText()) {
            handleMessage(update.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Message handler
    //
    // Chat membership events
    // -----------------------------------------------------------------------

    private void handleMyChatMember(ChatMemberUpdated update) {
        String newStatus = update.getNewChatMember().getStatus();
        String chatTitle = update.getChat().getTitle();
        Long chatId = update.getChat().getId();
        log.info("[ChatMember] chatId={} title='{}' newStatus={}", chatId, chatTitle, newStatus);

        switch (newStatus) {
            case "member", "administrator" -> registeredChatService.registerChat(chatId, chatTitle);
            case "kicked", "left" -> registeredChatService.deactivateChat(chatId);
        }
    }

    private void handleMessage(Message message) {
        String userName = message.getFrom().getUserName();
        Long userId = message.getFrom().getId();
        String text = message.getText().trim();
        Long chatId = message.getChatId();

        String chatType = message.getChat().getType();
        if ("group".equals(chatType) || "supergroup".equals(chatType)) {
            registeredChatService.registerChat(chatId, message.getChat().getTitle());
        }

        String[] tokens = text.split("\\s+", 2);
        String cmd = tokens[0].replaceAll("@.*$", Strings.EMPTY).toLowerCase();
        String arg = tokens.length > 1 ? tokens[1].trim() : Strings.EMPTY;

        switch (cmd) {
            case BotCommands.CMD_REGISTER -> handleRegister(chatId, userId, userName, arg);
            case BotCommands.CMD_UNREGISTER -> handleUnregister(chatId, userId, arg);
            case BotCommands.CMD_PROFILE -> handleProfile(chatId, userId, userName);
            case BotCommands.CMD_ABOUT -> sendText(chatId, botMessages.about());
            case BotCommands.CMD_STATISTIC_ALL -> sendAllStatistic(chatId);
            case BotCommands.CMD_STATISTIC -> handleStatistic(chatId, userId, userName, arg);
        }
    }

    // -----------------------------------------------------------------------
    // /statistic [optional @username]
    // -----------------------------------------------------------------------

    private void handleStatistic(Long chatId, Long callerId, String callerUsername, String arg) {
        log.info("[Command] /statistic chatId={} userId={} arg='{}'", chatId, callerUsername, arg);

        String text;
        if (!arg.isBlank()) {
            String username = arg.startsWith("@") ? arg.substring(1) : arg;
            log.info("[Statistic] By username='{}' chatId={}", username, chatId);

            Optional<UserStatistic> statisticOpt = statisticService.getByUsername(chatId, username);
            text = statisticOpt.isEmpty()
                    ? botMessages.statisticUserNotFound(username)
                    : botMessages.userStatisticCard(botMessages.statisticHeaderByUsername(username), statisticOpt.get());

        } else {
            log.info("[Statistic] Self userId={} chatId={}", callerId, chatId);

            Optional<UserStatistic> statisticOpt = statisticService.getByUserId(chatId, callerId);
            text = statisticOpt.isEmpty()
                    ? botMessages.statisticNotFound(callerUsername)
                    : botMessages.userStatisticCard(botMessages.statisticHeaderSelf(), statisticOpt.get());
        }
        sendText(chatId, text);
    }

    // -----------------------------------------------------------------------
    // /register /unregister /profile
    // -----------------------------------------------------------------------

    private void handleRegister(Long chatId, Long userId, String telegramUsername, String arg) {
        log.info("[Command] /register chatId={} userId={} arg='{}'", chatId, userId, arg);
        if (!arg.isBlank()) {
            RegisterResult result = dotaProfileService.register(chatId, userId, telegramUsername, arg);
            String text = RegisterResult.ALREADY_EXISTS == result
                    ? botMessages.dotaAlreadyExists(arg)
                    : botMessages.dotaRegistered(arg);
            sendText(chatId, text);
        }
        sendText(chatId, botMessages.dotaMissingNickname());
    }

    private void handleUnregister(Long chatId, Long userId, String arg) {
        log.info("[Command] /unregister chatId={} userId={}", chatId, userId);
        if (!arg.isBlank()) {
            boolean deleted = dotaProfileService.unregister(chatId, userId, arg);
            sendText(chatId, deleted
                    ? botMessages.dotaUnregistered(arg)
                    : botMessages.dotaNickNotFound(arg));
        }
        List<DotaProfile> nicknames = dotaProfileService.findAllByUser(chatId, userId);
        sendText(chatId, botMessages.dotaUnregisterUsage(nicknames));
    }

    private void handleProfile(Long chatId, Long userId, String telegramUsername) {
        log.info("[Command] /profile chatId={} userId={}", chatId, userId);
        List<DotaProfile> nicknames = dotaProfileService.findAllByUser(chatId, userId);
        String display = MessageFormattingUtility.displayName(userId, telegramUsername);
        sendText(chatId, botMessages.dotaMyProfile(display, nicknames));
    }

    // -----------------------------------------------------------------------
    // /statistic_all
    // -----------------------------------------------------------------------

    private void sendAllStatistic(Long chatId) {
        log.info("[Command] /statistic_all chatId={}", chatId);
        List<UserStatistic> statistics = statisticService.getChatStatistics(chatId);
        String text = statistics.isEmpty()
                ? botMessages.statisticAllEmpty()
                : botMessages.renderAllPlayersStatisticsTable(statistics);
        sendText(chatId, text);
    }

    /**
     * Step 1: Sends a voting message to the group.
     * If imageBytes are passed, sends it as a photo with a caption; otherwise, sends it as text.
     * hasPhoto is saved in the PollSession and used for subsequent edits.
     */
    public boolean startVote(Long chatId, String voteText, String matchId,
                             byte[] imageBytes, List<Team> teams, String memeComment) {
        log.info("[Bot] startVote chatId={} matchId={} hasImage={}", chatId, matchId, imageBytes != null);
        if (pollService.getActiveSession(chatId).isEmpty()) {
            try {
                boolean hasPhoto = imageBytes != null;
                String teamsJson = objectMapperConverter.serializeTeams(teams);

                Set<String> playerSides = statisticService.findPlayerSides(chatId, teamsJson);
                String playerTeamSide = playerSides.size() == 1 ? playerSides.iterator().next() : null;

                log.info("[Bot] startVote chatId={} matchId={} playerSide={}", chatId, matchId, playerTeamSide);

                Message sent = hasPhoto
                        ? sendPollWithPhoto(chatId, voteText, imageBytes, playerTeamSide)
                        : sendPollAsText(chatId, voteText, playerTeamSide);

                PollSession session = pollService.createSession(
                        chatId, sent.getMessageId(), matchId, hasPhoto, teamsJson, memeComment);
                log.info("[Bot] Poll STARTED chatId={} messageId={} sessionId={} matchId={} hasPhoto={} endsAt={}",
                        chatId, sent.getMessageId(), session.getId(), matchId, hasPhoto, session.getEndsAt());

                return true;
            } catch (TelegramApiException e) {
                log.error("[Bot] Failed to send poll to chatId={}: {}", chatId, e.getMessage(), e);
            }
        } else {
            log.warn("[Bot] Poll already active for chatId={}", chatId);
        }
        return false;
    }

    /**
     * Step 3: Publishes the match result to all chats where this matchId was launched.
     */
    public boolean summarizeVote(String matchId, String teamWon) {
        log.info("[Bot] summarizeVote matchId={} teamWon={}", matchId, teamWon);

        List<PollSession> sessions = pollService.findAllByMatchId(matchId);
        if (sessions.isEmpty()) {
            log.warn("[Bot] No sessions found for matchId={}", matchId);
            return false;
        }

        boolean anySuccess = false;

        for (PollSession session : sessions) {
            Long chatId = session.getChatId();
            if (session.getMatchStatus() == MatchStatus.SUMMARIZED) {
                log.warn("[Bot] sessionId={} chatId={} already SUMMARIZED — skipping",
                        session.getId(), chatId);
                continue;
            }

            if (session.getMatchStatus() == MatchStatus.ACTIVE) {
                log.warn("[Bot] summarize on ACTIVE — force-closing sessionId={} chatId={}",
                        session.getId(), chatId);
                closeVotingWindow(session);
            }

            VoteResult results = voteService.getResultsByMatchId(matchId, chatId);
            boolean mmrEnabled = results.totalVotes() >= PLAYER_NUMBER_THRESHOLD;
            Set<String> playerSides = statisticService.findPlayerSides(chatId, session.getTeamsJson());
            ThroneGame throne = statisticService.updateAfterMatch(matchId, chatId, teamWon, session.getTeamsJson());
            String summaryText = botMessages.matchSummary(results, teamWon, matchId, throne, playerSides, mmrEnabled);

            try {
                if (session.isHasPhoto()) {
                    // Telegram doesn't allow removing a photo from a message via edit.
                    // Delete the photo message and send a new text message.
                    deleteMessage(chatId, session.getMessageId());
                    sendTextNoPreview(chatId, summaryText);
                } else {
                    editMessageContentNoPreview(chatId, session.getMessageId(), summaryText);
                }
                pollService.markSummarized(session);

                log.info("[Bot] Summary posted sessionId={} chatId={} matchId={} teamWon={}",
                        session.getId(), chatId, matchId, teamWon);
                return true;
            } catch (TelegramApiException e) {
                log.error("[Bot] Failed to post summary sessionId={} chatId={}: {}",
                        session.getId(), chatId, e.getMessage(), e);
            }
        }
        return anySuccess;
    }

    // -----------------------------------------------------------------------
    // Callback - pressing the voting button
    // -----------------------------------------------------------------------

    private void handleCallback(CallbackQuery query) {
        String userName = query.getFrom().getUserName();
        Long userId = query.getFrom().getId();
        String callbackId = query.getId();
        String data = query.getData();

        MaybeInaccessibleMessage maybeMsg = query.getMessage();
        if (maybeMsg == null) {
            log.warn("[Callback] Null message for callbackId={}", callbackId);
            answerCallback(callbackId, botMessages.chatResolutionError(), true);
            return;
        }

        Long chatId = maybeMsg.getChat().getId();
        Integer messageId = maybeMsg.getMessageId();
        log.info("[Callback] chatId={} messageId={} userId={} username='{}' data='{}'",
                chatId, messageId, userId, userName, data);


        VoteType voteType;
        switch (data) {
            case KeyboardFactory.CALLBACK_RADIANT -> voteType = VoteType.RADIANT;
            case KeyboardFactory.CALLBACK_DIRE -> voteType = VoteType.DIRE;
            default -> {
                log.warn("[Callback] Unknown data='{}' userId={}", data, userId);
                answerCallback(callbackId, botMessages.unknownAction(), true);
                return;
            }
        }

        Optional<PollSession> sessionOpt = pollService.getSessionByMessageId(chatId, messageId);
        if (sessionOpt.isEmpty() || sessionOpt.get().getMatchStatus() != MatchStatus.ACTIVE) {
            log.warn("[Callback] No active poll chatId={} messageId={} userId={}", chatId, messageId, userId);
            answerCallback(callbackId, botMessages.votingAlreadyClosed(), true);
            return;
        }

        PollSession session = sessionOpt.get();
        boolean accepted = voteService.castVote(chatId, userId, userName, voteType, session.getMatchId());
        log.info("[Callback] Vote {} chatId={} userId={} matchId={} type={}",
                accepted ? "ACCEPTED" : "REJECTED", chatId, userId, session.getMatchId(), voteType);

        if (!accepted) {
            answerCallback(callbackId, botMessages.voteAlreadyCast(), true);
            return;
        }
        String personalMessage = resolvePersonalVoteMessage(chatId, userId, voteType, session); // Check if the user is a participant in the match
        answerCallback(callbackId, personalMessage, false);
    }

    // -----------------------------------------------------------------------
    // Scheduler — Step 2: Close in 1 minute
    // -----------------------------------------------------------------------

    @Scheduled(fixedDelay = 15_000)
    public void closeExpiredPolls() {
        List<PollSession> expired = pollService.findExpiredSessions();
        if (!expired.isEmpty()) {
            log.info("[Scheduler] Closing {} expired session(s)", expired.size());

            for (PollSession session : expired) {
                try {
                    closeVotingWindow(session);
                } catch (Exception e) {
                    log.error("[Scheduler] Error closing sessionId={}: {}", session.getId(), e.getMessage(), e);
                }
            }
        }
    }

    private void closeVotingWindow(PollSession session) {
        Integer messageId = session.getMessageId();
        String matchId = session.getMatchId();
        Long chatId = session.getChatId();

        VoteResult results = voteService.getResultsByMatchId(matchId, chatId);
        boolean has322 = statisticService.hasBetrayal(matchId, chatId, session.getTeamsJson());
        Set<String> playerSides = statisticService.findPlayerSides(chatId, session.getTeamsJson());

        log.info("[Scheduler] closeVotingWindow sessionId={} matchId={} chatId={} hasPhoto={} wins={} loses={} has322={}",
                session.getId(), matchId, chatId, session.isHasPhoto(), results.wins(), results.loses(), has322);

        try {
            telegramClient.execute(EditMessageReplyMarkup.builder() // Remove buttons
                    .chatId(chatId.toString())
                    .messageId(messageId)
                    .replyMarkup(null)
                    .build());

            String closedText = botMessages.votingClosed(results, session.getMemeComment(), has322, playerSides);
            editMessageContent(chatId, messageId, closedText, session.isHasPhoto());

            pollService.markVotingClosed(session);
            log.info("[Scheduler] VOTING_CLOSED sessionId={} matchId={} chatId={}",
                    session.getId(), matchId, chatId);
        } catch (TelegramApiException e) {
            log.warn("[Scheduler] Edit failed chatId={}: {}. Marking closed anyway.", chatId, e.getMessage());
            pollService.markVotingClosed(session);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers — sending and editing messages
    // -----------------------------------------------------------------------

    /**
     * Edits the text or caption of a message depending on
     * whether it is a photo (hasPhoto=true → EditMessageCaption)
     * or plain text (hasPhoto=false → EditMessageText).
     */
    private void editMessageContent(Long chatId, Integer messageId, String content, boolean hasPhoto) throws TelegramApiException {
        if (hasPhoto) {
            telegramClient.execute(EditMessageCaption.builder()
                    .chatId(chatId.toString())
                    .parseMode(PARSE_MODE)
                    .messageId(messageId)
                    .caption(content)
                    .build());
        } else {
            telegramClient.execute(EditMessageText.builder()
                    .chatId(chatId.toString())
                    .parseMode(PARSE_MODE)
                    .messageId(messageId)
                    .text(content)
                    .build());
        }
    }

    private Message sendPollWithPhoto(Long chatId, String caption, byte[] imageBytes, String playerTeamSide) throws TelegramApiException {
        log.debug("[Bot] Sending poll as photo chatId={} imageSize={}b", chatId, imageBytes.length);
        return telegramClient.execute(SendPhoto.builder()
                .photo(new InputFile(new ByteArrayInputStream(imageBytes), IMAGE_FILENAME))
                .replyMarkup(keyboardFactory.votingKeyboard(playerTeamSide))
                .chatId(chatId.toString())
                .parseMode(PARSE_MODE)
                .caption(caption)
                .build());
    }

    private Message sendPollAsText(Long chatId, String text, String playerTeamSide) throws TelegramApiException {
        log.debug("[Bot] Sending poll as text chatId={}", chatId);
        return telegramClient.execute(SendMessage.builder()
                .replyMarkup(keyboardFactory.votingKeyboard(playerTeamSide))
                .chatId(chatId.toString())
                .parseMode(PARSE_MODE)
                .text(text)
                .build());
    }

    private void deleteMessage(Long chatId, Integer messageId) throws TelegramApiException {
        log.debug("[Bot] Deleting messageId={} chatId={}", messageId, chatId);
        telegramClient.execute(DeleteMessage.builder()
                .chatId(chatId.toString())
                .messageId(messageId)
                .build());
    }

    /**
     * Sends a text message without a pop-up link preview.
     */
    private void sendTextNoPreview(Long chatId, String text) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .linkPreviewOptions(LinkPreviewOptions.builder()
                            .isDisabled(true)
                            .build())
                    .chatId(chatId.toString())
                    .parseMode(PARSE_MODE)
                    .text(text)
                    .build());
        } catch (TelegramApiException e) {
            log.error("[Bot] sendTextNoPreview failed chatId={}: {}", chatId, e.getMessage());
        }
    }

    /**
     * Edits a text message without the pop-up link preview.
     */
    private void editMessageContentNoPreview(Long chatId, Integer messageId, String content) throws TelegramApiException {
        telegramClient.execute(EditMessageText.builder()
                .linkPreviewOptions(LinkPreviewOptions.builder()
                        .isDisabled(true)
                        .build())
                .chatId(chatId.toString())
                .parseMode(PARSE_MODE)
                .messageId(messageId)
                .text(content)
                .build());
    }

    /**
     * Defines a personal message when voting.
     * Otherwise, a standard vote confirmation.
     */
    private String resolvePersonalVoteMessage(Long chatId, Long userId, VoteType voteType, PollSession session) {
        List<Team> teams = objectMapperConverter.deserializeTeams(session.getTeamsJson());
        if (teams == null || teams.isEmpty()) {
            return botMessages.voteAccepted(voteType);
        }

        List<String> userNicknames = dotaProfileService.findAllByUser(chatId, userId).stream()
                .map(DotaProfile::getDotaNickname)
                .toList();
        if (userNicknames.isEmpty()) {
            return botMessages.voteAccepted(voteType);
        }

        Set<String> matchNicknames = dotaProfileService.extractMatchNicknames(teams);
        Optional<String> matchedNickname = dotaProfileService.findMatchingNick(userNicknames, matchNicknames);
        if (matchedNickname.isEmpty()) {
            return botMessages.voteAccepted(voteType);
        }

        Optional<String> playerTeam = dotaProfileService.findPlayerTeam(matchedNickname.get(), teams);
        if (playerTeam.isEmpty()) {
            return botMessages.voteAccepted(voteType);
        }

        boolean isOwnTeam = (VoteType.RADIANT.equalsToSideName(playerTeam.get()) && voteType == VoteType.RADIANT)
                || (VoteType.DIRE.equalsToSideName(playerTeam.get()) && voteType == VoteType.DIRE);
        log.info("[Callback] Player nick='{}' team={} votedFor={} isOwnTeam={}",
                matchedNickname.get(), playerTeam.get(), voteType, isOwnTeam);
        return isOwnTeam
                ? botMessages.voteForOwnTeam()
                : botMessages.voteAgainstOwnTeam();
    }

    private void sendText(Long chatId, String text) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .parseMode(PARSE_MODE)
                    .text(text)
                    .build());
        } catch (TelegramApiException e) {
            log.error("[Bot] sendText failed chatId={}: {}", chatId, e.getMessage());
        }
    }

    private void answerCallback(String callbackQueryId, String text, boolean showAlert) {
        try {
            telegramClient.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQueryId)
                    .showAlert(showAlert)
                    .text(text)
                    .build());
        } catch (TelegramApiException e) {
            log.error("[Callback] Failed to answer callbackId={}: {}", callbackQueryId, e.getMessage());
        }
    }
}