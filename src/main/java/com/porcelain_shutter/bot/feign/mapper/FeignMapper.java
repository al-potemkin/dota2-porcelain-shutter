package com.porcelain_shutter.bot.feign.mapper;

import com.porcelain_shutter.bot.dto.Team;
import com.porcelain_shutter.bot.feign.request.CollageRequest;
import jakarta.annotation.Nullable;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface FeignMapper {
    List<CollageRequest> toCollageRequests(List<Team> teams);

    @Mapping(source = "teamSide", target = "team")
    @Mapping(source = "players", target = "characters")
    CollageRequest toCollageRequest(Team team);

    @Nullable
    default List<String> mapPlayersToCharacters(List<Team.Player> players) {
        if (players != null) {
            return players.stream()
                    .map(Team.Player::getCharacter)
                    .toList();
        }
        return null;

    }
}
