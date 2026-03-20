package com.porcelain_shutter.bot.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum VoteType {
    RADIANT("Radiant"), DIRE("Dire");

    private final String sideName;

    public static VoteType findBySideName(String sideName) {
        return Arrays.stream(values())
                .filter(value -> value.sideName.equalsIgnoreCase(sideName))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("Unknown side: ".concat(sideName)));
    }

    public boolean equalsToSideName(String sideName) {
        return sideName != null && this.sideName.equalsIgnoreCase(sideName.trim());
    }
}