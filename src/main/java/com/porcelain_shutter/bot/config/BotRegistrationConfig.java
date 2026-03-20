package com.porcelain_shutter.bot.config;

import com.porcelain_shutter.bot.handler.MatchVotingBot;
import com.porcelain_shutter.bot.service.LocaleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeAllGroupChats;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class BotRegistrationConfig {

    private final LocaleService localeService;

    @Bean
    public TelegramBotsLongPollingApplication telegramBotsLongPollingApplication(
            @Value("${telegram.bot.token}") String token,
            MatchVotingBot matchVotingBot
    ) {
        try {
            TelegramBotsLongPollingApplication app = new TelegramBotsLongPollingApplication();
            app.registerBot(token, matchVotingBot);
            log.info("[BotRegistrationConfig] Bot registered - long polling started");
            return app;
        } catch (Exception e) {
            throw new RuntimeException("Failed to register Telegram bot", e);
        }
    }

    @Bean
    public TelegramClient telegramClient(@Value("${telegram.bot.token}") String token) {
        TelegramClient client = new OkHttpTelegramClient(token);
        registerCommands(client);
        return client;
    }

    private void registerCommands(TelegramClient client) {
        List<BotCommand> commands = List.of(
                new BotCommand(BotCommands.CMD_STATISTIC, localeService.get("bot.command.statistic")),
                new BotCommand(BotCommands.CMD_STATISTIC_ALL, localeService.get("bot.command.statistic_all")),
                new BotCommand(BotCommands.CMD_REGISTER, localeService.get("bot.command.register")),
                new BotCommand(BotCommands.CMD_UNREGISTER, localeService.get("bot.command.unregister")),
                new BotCommand(BotCommands.CMD_PROFILE, localeService.get("bot.command.profile")),
                new BotCommand(BotCommands.CMD_ABOUT, localeService.get("bot.command.about"))
        );
        try {
            client.execute(SetMyCommands.builder()
                    .commands(commands)
                    .scope(new BotCommandScopeAllGroupChats())
                    .build());
            log.info("[BotRegistrationConfig] Commands registration: commands={}",
                    commands.stream().map(BotCommand::getCommand).toList());
        } catch (TelegramApiException e) {
            log.error("[BotRegistrationConfig] Failed to register commands: {}", e.getMessage(), e);
        }
    }
}
