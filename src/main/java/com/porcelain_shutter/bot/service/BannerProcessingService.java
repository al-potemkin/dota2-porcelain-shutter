package com.porcelain_shutter.bot.service;

import com.porcelain_shutter.bot.dto.Team;
import com.porcelain_shutter.bot.feign.ImageProcessorFeign;
import com.porcelain_shutter.bot.feign.mapper.FeignMapper;
import com.porcelain_shutter.bot.feign.request.CollageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BannerProcessingService {
    private final Optional<ImageProcessorFeign> imageProcessorFeign;
    private final FeignMapper feignMapper;

    public byte[] getBannerImageBytes(List<Team> teams) {
        log.info("[BannerProcessingService] Receiving a message banner");
        return imageProcessorFeign.map(
                        ipf -> {
                            List<CollageRequest> collageRequest = feignMapper.toCollageRequests(teams);
                            return decodeImage(ipf.generateCollageInBase64(collageRequest));
                        })
                .orElse(null);
    }

    /**
     * Decodes a Base64 image string to bytes.
     * Returns null if the value is absent or blank — bot will send plain text instead.
     */
    private static byte[] decodeImage(String imageBase64) {
        if (imageBase64 != null && !imageBase64.isBlank()) {
            try {
                return Base64.getDecoder().decode(imageBase64.trim());
            } catch (IllegalArgumentException e) {
                log.warn("[BannerProcessingService] Invalid Base64 image — will send without image: {}", e.getMessage());
            }
        }
        return null;
    }
}
