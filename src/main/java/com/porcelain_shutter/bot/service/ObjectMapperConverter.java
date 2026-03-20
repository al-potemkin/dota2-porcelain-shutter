package com.porcelain_shutter.bot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.porcelain_shutter.bot.dto.Team;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ObjectMapperConverter {
    private final ObjectMapper objectMapper;

    public List<Team> deserializeTeams(String teamsJson) {
        if (teamsJson != null && !teamsJson.isBlank()) {
            try {
                return objectMapper.readValue(teamsJson, new TypeReference<List<Team>>() {
                });
            } catch (JsonProcessingException e) {
                log.error("[Bot] Failed to deserialize teams: {}", e.getMessage());
            }
        }
        return null;
    }

    public String serializeTeams(List<Team> teams) {
        if (teams != null) {
            try {
                return objectMapper.writeValueAsString(teams);
            } catch (JsonProcessingException e) {
                log.error("[Bot] Failed to serialize teams: {}", e.getMessage());
            }
        }
        return null;
    }
}
