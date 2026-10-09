package com.carevoice.service;
import com.carevoice.exception.VoiceUploadException;

import com.carevoice.config.CareVoiceVoiceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class VoiceUploadValidator {
    private static final Logger log = LoggerFactory.getLogger(VoiceUploadValidator.class);

    private final CareVoiceVoiceProperties properties;

    public VoiceUploadValidator(CareVoiceVoiceProperties properties) {
        this.properties = properties;
    }

    public String validate(MultipartFile audio) {
        if (audio == null) {
            throw new VoiceUploadException(VoiceUploadException.REQUIRED);
        }
        if (audio.isEmpty()) {
            throw new VoiceUploadException(VoiceUploadException.EMPTY);
        }
        String canonical = SupportedAudioTypes.canonical(audio.getContentType()).orElse(null);
        if (canonical == null) {
            log.info("Voice upload rejected due to unsupported format");
            throw new VoiceUploadException(VoiceUploadException.UNSUPPORTED);
        }
        if (audio.getSize() > properties.maxFileSizeBytes()) {
            throw new VoiceUploadException(VoiceUploadException.tooLarge(properties.getMaxFileSizeMb()));
        }
        return canonical;
    }
}
