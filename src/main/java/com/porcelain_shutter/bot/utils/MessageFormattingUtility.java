package com.porcelain_shutter.bot.utils;

import com.porcelain_shutter.bot.service.StatisticService;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MessageFormattingUtility {
    public String displayName(Long userId, String telegramUsername) {
        return (telegramUsername != null && !telegramUsername.isBlank())
                ? "@" + telegramUsername
                : StatisticService.USER + userId;
    }
}
