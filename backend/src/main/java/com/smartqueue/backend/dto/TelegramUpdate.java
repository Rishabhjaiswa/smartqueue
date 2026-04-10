package com.smartqueue.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelegramUpdate {

    @JsonProperty("update_id")
    private Long updateId;

    @JsonProperty("message")
    private TelegramMessage message;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramMessage {

        @JsonProperty("message_id")
        private Long messageId;

        @JsonProperty("chat")
        private TelegramChat chat;

        @JsonProperty("text")
        private String text;

        @JsonProperty("from")
        private TelegramUser from;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramChat {

        @JsonProperty("id")
        private Long id;

        @JsonProperty("first_name")
        private String firstName;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramUser {

        @JsonProperty("id")
        private Long id;

        @JsonProperty("first_name")
        private String firstName;

        @JsonProperty("language_code")
        private String languageCode;
    }
}