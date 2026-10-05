package com.carevoice.api;

import com.carevoice.voice.VoiceMonitoringResponse;
import com.carevoice.voice.VoiceMonitoringService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/monitoring")
@ConditionalOnProperty(prefix = "carevoice.voice", name = "enabled", havingValue = "true")
public class VoiceMonitoringController {
    private final VoiceMonitoringService voiceMonitoring;

    public VoiceMonitoringController(VoiceMonitoringService voiceMonitoring) {
        this.voiceMonitoring = voiceMonitoring;
    }

    @PostMapping(path = "/sessions/{sessionId}/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VoiceMonitoringResponse voice(@PathVariable Long sessionId, @RequestPart("audio") MultipartFile audio) {
        return voiceMonitoring.process(sessionId, audio);
    }
}
