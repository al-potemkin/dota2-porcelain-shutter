package com.porcelain_shutter.bot.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Represents a single team from the incoming JSON payload.
 * <p>
 * Expected structure:
 * <pre>
 * [
 *   {
 *     "teamSide": "Radiant",
 *     "players": [
 *       { "nickname": "Player 0", "character": "Axe" },
 *       ...
 *     ]
 *   },
 *   ...
 * ]
 * </pre>
 */
@Data
public class Team {

    @NotBlank(message = "teamSide must not be blank")
    private String teamSide;

    @Valid
    @NotEmpty(message = "players must not be empty")
    @Size(min = 1, max = 5, message = "team must have between 1 and 5 players")
    private List<Player> players;

    @Data
    public static class Player {

        @NotBlank(message = "Player nickname must not be blank")
        @Size(max = 100, message = "Nickname must not exceed 100 characters")
        private String nickname;

        @NotBlank(message = "Player character must not be blank")
        @Size(max = 100, message = "Character must not exceed 100 characters")
        private String character;
    }
}