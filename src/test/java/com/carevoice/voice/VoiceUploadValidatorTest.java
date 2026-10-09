package com.carevoice.voice;
import com.carevoice.exception.VoiceUploadException;
import com.carevoice.service.VoiceUploadValidator;

import com.carevoice.config.CareVoiceVoiceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VoiceUploadValidatorTest {

    @Test
    void rejectsEmptyAndUnsupportedUploads() {
        VoiceUploadValidator validator = validator(20);

        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(VoiceUploadException.class)
                .hasMessage(VoiceUploadException.REQUIRED);
        assertThatThrownBy(() -> validator.validate(file("audio/wav", new byte[0])))
                .isInstanceOf(VoiceUploadException.class)
                .hasMessage(VoiceUploadException.EMPTY);
        assertThatThrownBy(() -> validator.validate(file("text/plain", new byte[]{1})))
                .isInstanceOf(VoiceUploadException.class)
                .hasMessage(VoiceUploadException.UNSUPPORTED);
    }

    @Test
    void rejectsFilesLargerThanTheConfiguredLimit() {
        VoiceUploadValidator validator = validator(1);
        byte[] tooLarge = new byte[(1 * 1024 * 1024) + 1];

        assertThatThrownBy(() -> validator.validate(file("audio/mpeg", tooLarge)))
                .isInstanceOf(VoiceUploadException.class)
                .hasMessage(VoiceUploadException.tooLarge(1));
    }

    @Test
    void mapsBrowserAliasesOntoSdkMimeTypes() {
        VoiceUploadValidator validator = validator(20);

        assertThat(validator.validate(file("audio/x-wav", new byte[]{1}))).isEqualTo("audio/wav");
        assertThat(validator.validate(file("audio/wave", new byte[]{1}))).isEqualTo("audio/wav");
        assertThat(validator.validate(file("audio/vnd.wave", new byte[]{1}))).isEqualTo("audio/wav");
        assertThat(validator.validate(file("audio/mp4", new byte[]{1}))).isEqualTo("audio/mp4");
        assertThat(validator.validate(file("audio/x-m4a", new byte[]{1}))).isEqualTo("audio/m4a");
        assertThat(validator.validate(file("audio/webm;codecs=opus", new byte[]{1}))).isEqualTo("audio/webm");
        assertThat(validator.validate(file("audio/ogg", new byte[]{1}))).isEqualTo("audio/ogg");
        assertThat(validator.validate(file("audio/flac", new byte[]{1}))).isEqualTo("audio/flac");
        assertThat(validator.validate(file("audio/mp3", new byte[]{1}))).isEqualTo("audio/mpeg");
        assertThatThrownBy(() -> validator.validate(file("audio/aiff", new byte[]{1})))
                .isInstanceOf(VoiceUploadException.class)
                .hasMessage(VoiceUploadException.UNSUPPORTED);
    }

    @Test
    void defaultLimitIsTwentyMegabytes() {
        assertThat(new CareVoiceVoiceProperties().maxFileSizeBytes()).isEqualTo(20L * 1024 * 1024);
    }

    private static VoiceUploadValidator validator(int maxFileSizeMb) {
        CareVoiceVoiceProperties properties = new CareVoiceVoiceProperties();
        properties.setMaxFileSizeMb(maxFileSizeMb);
        return new VoiceUploadValidator(properties);
    }

    private static MockMultipartFile file(String contentType, byte[] bytes) {
        return new MockMultipartFile("audio", "recording.bin", contentType, bytes);
    }
}
