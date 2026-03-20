package com.porcelain_shutter.bot.feign.request;

import lombok.Data;

import java.util.List;

/**
 * DTO for requesting the d2collage service.
 */
@Data
public class CollageRequest {
    private String team;
    private List<String> characters;
}