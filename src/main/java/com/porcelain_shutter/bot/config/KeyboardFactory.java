package com.porcelain_shutter.bot.config;

import com.porcelain_shutter.bot.entity.VoteType;
import com.porcelain_shutter.bot.service.LocaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.List;

@Component
@RequiredArgsConstructor
public class KeyboardFactory {

    public static final String CALLBACK_RADIANT = "vote:Radiant";
    public static final String CALLBACK_DIRE = "vote:Dire";

    private final LocaleService localeService;

    /**
     * Returns the two-button voting keyboard:
     * [Radiant] [Dire]
     */
    public InlineKeyboardMarkup votingKeyboard(String playerTeamSide) {
        boolean isRadiant = VoteType.RADIANT.equalsToSideName(playerTeamSide);
        boolean isDire = VoteType.DIRE.equalsToSideName(playerTeamSide);

        InlineKeyboardButton radiantButton = InlineKeyboardButton.builder()
                .text(localeService.get(isRadiant
                                ? "bot.keyboard.pick"
                                : "bot.keyboard.radiant")
                        .formatted(VoteType.RADIANT.getSideName()))
                .callbackData(CALLBACK_RADIANT)
                .style("success") // Green button
                .build();

        InlineKeyboardButton direButton = InlineKeyboardButton.builder()
                .text(localeService.get(isDire
                                ? "bot.keyboard.pick"
                                : "bot.keyboard.dire")
                        .formatted(VoteType.DIRE.getSideName()))
                .callbackData(CALLBACK_DIRE)
                .style("danger") // Red button
                .build();

        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(List.of(radiantButton, direButton)))
                .build();
    }
}
