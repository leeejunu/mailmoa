package com.example.mailmoa.mail.infrastructure.gmail;

import com.example.mailmoa.mail.application.dto.MailSyncData;
import com.example.mailmoa.mail.application.dto.SyncResponseResult;
import com.example.mailmoa.mail.application.dto.TokenRefreshResult;
import com.example.mailmoa.mail.application.port.GmailPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GmailAdapter implements GmailPort {

    private static final String GMAIL_API_BASE = "https://gmail.googleapis.com/gmail/v1/users/me";
    private static final int PAGE_SIZE = 100;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    private final RestClient restClient = RestClient.create();

    @Override
    public TokenRefreshResult refreshAccessToken(String refreshToken) {
        String body = "grant_type=refresh_token&refresh_token=" + refreshToken
                + "&client_id=" + clientId + "&client_secret=" + clientSecret;

        JsonNode response = restClient.post()
                .uri("https://oauth2.googleapis.com/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        String accessToken = response.get("access_token").asText();
        long expiresIn = response.path("expires_in").asLong(3600);
        return new TokenRefreshResult(accessToken, expiresIn);
    }

    @Override
    public void trashMail(String accessToken, String externalMessageId) {
        restClient.post()
                .uri(GMAIL_API_BASE + "/messages/{id}/trash", externalMessageId)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public SyncResponseResult fetchMails(String accessToken, String lastHistoryId) {
        if (lastHistoryId == null) {
            return fullSync(accessToken);
        }
        return incrementalSync(accessToken, lastHistoryId);
    }

    // 최초 동기화: 전체 메일 가져오고 현재 historyId 저장
    private SyncResponseResult fullSync(String accessToken) {
        List<String> messageIds = fetchAllMessageIds(accessToken);
        List<MailSyncData> mails = messageIds.stream()
                .map(id -> fetchMailDetail(accessToken, id))
                .toList();
        String historyId = fetchCurrentHistoryId(accessToken);
        return new SyncResponseResult(mails, historyId);
    }

    // 이후 동기화: history API로 새 메일 ID만 가져오기
    private SyncResponseResult incrementalSync(String accessToken, String lastHistoryId) {
        List<String> newMessageIds = new ArrayList<>();
        String pageToken = null;
        String latestHistoryId = lastHistoryId;

        do {
            String uri = GMAIL_API_BASE + "/history?startHistoryId=" + lastHistoryId
                    + "&historyTypes=messageAdded&maxResults=" + PAGE_SIZE;
            if (pageToken != null) {
                uri += "&pageToken=" + pageToken;
            }

            JsonNode response = restClient.get()
                    .uri(uri)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) break;

            if (response.has("historyId")) {
                latestHistoryId = response.get("historyId").asText();
            }

            if (response.has("history")) {
                for (JsonNode history : response.get("history")) {
                    if (history.has("messagesAdded")) {
                        for (JsonNode added : history.get("messagesAdded")) {
                            newMessageIds.add(added.path("message").path("id").asText());
                        }
                    }
                }
            }

            pageToken = response.has("nextPageToken")
                    ? response.get("nextPageToken").asText() : null;

        } while (pageToken != null);

        List<MailSyncData> mails = newMessageIds.stream()
                .map(id -> fetchMailDetail(accessToken, id))
                .toList();

        return new SyncResponseResult(mails, latestHistoryId);
    }

    private String fetchCurrentHistoryId(String accessToken) {
        JsonNode profile = restClient.get()
                .uri(GMAIL_API_BASE + "/profile")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class);
        return profile != null ? profile.path("historyId").asText(null) : null;
    }

    private List<String> fetchAllMessageIds(String accessToken) {
        List<String> ids = new ArrayList<>();
        String pageToken = null;

        do {
            String uri = GMAIL_API_BASE + "/messages?maxResults=" + PAGE_SIZE;
            if (pageToken != null) {
                uri += "&pageToken=" + pageToken;
            }

            JsonNode response = restClient.get()
                    .uri(uri)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) break;

            if (response.has("messages")) {
                for (JsonNode message : response.get("messages")) {
                    ids.add(message.get("id").asText());
                }
            }

            pageToken = response.has("nextPageToken")
                    ? response.get("nextPageToken").asText() : null;

        } while (pageToken != null);

        return ids;
    }

    private MailSyncData fetchMailDetail(String accessToken, String messageId) {
        JsonNode message = restClient.get()
                .uri(GMAIL_API_BASE + "/messages/" + messageId + "?format=full")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class);

        String subject = "";
        String from = "";
        if (message != null && message.has("payload")) {
            JsonNode payload = message.get("payload");
            if (payload.has("headers")) {
                for (JsonNode header : payload.get("headers")) {
                    String name = header.get("name").asText();
                    String value = header.get("value").asText();
                    switch (name) {
                        case "Subject" -> subject = value;
                        case "From" -> from = value;
                    }
                }
            }
        }

        String snippet = message != null && message.has("snippet")
                ? message.get("snippet").asText() : "";

        long internalDate = message != null && message.has("internalDate")
                ? message.get("internalDate").asLong() : 0;
        LocalDateTime receivedAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(internalDate), ZoneId.systemDefault());

        JsonNode payload = message != null ? message.get("payload") : null;
        Map<String, String> cidMap = new HashMap<>();
        collectInlineImages(accessToken, messageId, payload, cidMap);

        String body = extractBody(payload);
        for (Map.Entry<String, String> entry : cidMap.entrySet()) {
            body = body.replace("cid:" + entry.getKey(), entry.getValue());
        }

        String[] senderParts = parseSender(from);
        return new MailSyncData(messageId, subject, senderParts[0], senderParts[1], snippet, body, receivedAt, "GMAIL");
    }

    private void collectInlineImages(String accessToken, String messageId, JsonNode payload, Map<String, String> cidMap) {
        if (payload == null) return;

        String mimeType = payload.path("mimeType").asText("");
        if (mimeType.startsWith("image/")) {
            String contentId = getContentId(payload);
            if (contentId != null) {
                String dataUri = fetchImageAsDataUri(accessToken, messageId, payload, mimeType);
                if (dataUri != null) cidMap.put(contentId, dataUri);
            }
        }

        if (payload.has("parts")) {
            for (JsonNode part : payload.get("parts")) {
                collectInlineImages(accessToken, messageId, part, cidMap);
            }
        }
    }

    private String getContentId(JsonNode part) {
        if (!part.has("headers")) return null;
        for (JsonNode header : part.get("headers")) {
            if ("Content-ID".equalsIgnoreCase(header.path("name").asText())) {
                return header.path("value").asText().trim().replaceAll("^<|>$", "");
            }
        }
        return null;
    }

    private String fetchImageAsDataUri(String accessToken, String messageId, JsonNode part, String mimeType) {
        String data = part.path("body").path("data").asText("");
        if (!data.isEmpty()) return "data:" + mimeType + ";base64," + data;

        String attachmentId = part.path("body").path("attachmentId").asText("");
        if (attachmentId.isEmpty()) return null;

        try {
            JsonNode response = restClient.get()
                    .uri(GMAIL_API_BASE + "/messages/" + messageId + "/attachments/" + attachmentId)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);

            String attachmentData = response != null ? response.path("data").asText("") : "";
            return attachmentData.isEmpty() ? null : "data:" + mimeType + ";base64," + attachmentData;
        } catch (Exception e) {
            log.warn("Failed to fetch attachment {} for message {}", attachmentId, messageId);
            return null;
        }
    }

    private String extractBody(JsonNode payload) {
        if (payload == null) return "";

        String mimeType = payload.path("mimeType").asText("");
        if (mimeType.equals("text/html") || mimeType.equals("text/plain")) {
            return decodeBase64(payload.path("body").path("data").asText(""));
        }

        if (payload.has("parts")) {
            String plain = "";
            for (JsonNode part : payload.get("parts")) {
                String partMime = part.path("mimeType").asText("");
                if (partMime.equals("text/html")) {
                    return decodeBase64(part.path("body").path("data").asText(""));
                }
                if (partMime.equals("text/plain")) {
                    plain = decodeBase64(part.path("body").path("data").asText(""));
                }
                if (partMime.startsWith("multipart/")) {
                    String nested = extractBody(part);
                    if (!nested.isEmpty()) return nested;
                }
            }
            return plain;
        }

        return "";
    }

    private String decodeBase64(String data) {
        if (data == null || data.isEmpty()) return "";
        try {
            return new String(Base64.getUrlDecoder().decode(data), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private String[] parseSender(String from) {
        if (from.contains("<")) {
            String name = from.substring(0, from.indexOf("<")).trim().replace("\"", "");
            String email = from.substring(from.indexOf("<") + 1, from.indexOf(">")).trim();
            return new String[]{name, email};
        }
        return new String[]{"", from.trim()};
    }
}
