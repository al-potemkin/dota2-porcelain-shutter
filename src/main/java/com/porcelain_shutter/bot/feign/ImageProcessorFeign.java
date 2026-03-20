package com.porcelain_shutter.bot.feign;

import com.porcelain_shutter.bot.feign.request.CollageRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(
        name = "${feign.clients.d2collage-service.name:d2collage-service}",
        url = "${feign.clients.d2collage-service.url}"
)
@ConditionalOnProperty(name = "feign.clients.d2collage-service.enable", havingValue = "true")
public interface ImageProcessorFeign {
    @PostMapping(path = "/api/collage/base64", consumes = MediaType.APPLICATION_JSON_VALUE)
    String generateCollageInBase64(@RequestBody List<CollageRequest> teams);
}
