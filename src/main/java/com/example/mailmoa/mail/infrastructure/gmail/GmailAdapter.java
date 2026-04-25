package com.example.mailmoa.mail.infrastructure.gmail;

import com.example.mailmoa.mail.application.dto.MailSyncData;
import com.example.mailmoa.mail.application.dto.SyncResponseResult;
import com.example.mailmoa.mail.application.dto.TokenRefreshResult;
import com.example.mailmoa.mail.application.port.GmailPort;
import com.example.mailmoa.mail.application.port.GmailWatchPort;
import com.example.mailmoa.mail.application.port.MailBodyPort;
import com.example.mailmoa.mail.application.port.MailSyncPort;
import com.example.mailmoa.mailaccount.domain.model.MailAccount;
import com.example.mailmoa.mailaccount.domain.model.MailProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import jakarta.mail.internet.InternetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

@Slf4j
@Component
@RequiredArgsConstructor
public class GmailAdapter implements GmailPort, GmailWatchPort, MailSyncPort, MailBodyPort {

    private static final String GMAIL_API_BASE = "https://gmail.googleapis.com/gmail/v1/users/me";
    private static final int PAGE_SIZE = 100;
    private static final int CONCURRENCY = 200;
    // 동시 요청 수를 제한하는 세마포어 (rate limit 대응)
    private static final Semaphore RATE_LIMITER = new Semaphore(CONCURRENCY);

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    private final ObjectMapper objectMapper;
    // JdkClientHttpRequestFactory: java.net.http.HttpClient 사용 → synchronized 블록 없음 → 가상 스레드 pinning 없음
    private final RestClient restClient = RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory())
            .build();

    // -------------------------------------------------------------------------
    // MailSyncPort
    // -------------------------------------------------------------------------

    @Override
    public MailProvider getSupportedProvider() {
        return MailProvider.GMAIL;
    }

    @Override
    public SyncResponseResult fetchMails(MailAccount account, String credential) {
        if (account.getLastHistoryId() == null) {
            return fullSync(credential);
        }
        return incrementalSync(credential, account.getLastHistoryId());
    }

    @Override
    public List<MailSyncData> fetchRemaining(MailAccount account, String credential, String continuationToken) {
        List<String> ids = collectAllPageIds(credential, continuationToken);
        return fetchMessagesParallel(credential, ids);
    }

    // -------------------------------------------------------------------------
    // GmailWatchPort
    // -------------------------------------------------------------------------

    @Override
    public void setupWatch(String accessToken, String topicName) {
        Map<String, Object> body = Map.of(
                "topicName", topicName,
                "labelIds", List.of("INBOX")
        );
        restClient.post()
                .uri(GMAIL_API_BASE + "/watch")
                .header("Authorization", "Bearer " + accessToken)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    // -------------------------------------------------------------------------
    // GmailPort
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // 내부 - full sync
    // -------------------------------------------------------------------------

    private SyncResponseResult fullSync(String accessToken) {
        // profile 조회와 전체 ID 수집을 가상 스레드로 병렬 실행
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var historyIdFuture = CompletableFuture.supplyAsync(
                    () -> fetchProfileHistoryId(accessToken), executor);
            var idsFuture = CompletableFuture.supplyAsync(
                    () -> collectAllPageIds(accessToken, null), executor);

            String historyId = historyIdFuture.join();
            List<String> allIds = idsFuture.join();
            log.info("[Gmail ID 수집 완료] 총 {}개", allIds.size());

            List<MailSyncData> mails = fetchMessagesParallel(accessToken, allIds);
            log.info("[Gmail 전체 동기화] {}개 완료", mails.size());
            return new SyncResponseResult(mails, historyId, null);
        }
    }

    private String fetchProfileHistoryId(String accessToken) {
        JsonNode profile = restClient.get()
                .uri(GMAIL_API_BASE + "/profile")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class);
        return profile != null ? profile.path("historyId").asText(null) : null;
    }

    // -------------------------------------------------------------------------
    // 내부 - incremental sync
    // -------------------------------------------------------------------------

    private SyncResponseResult incrementalSync(String accessToken, String lastHistoryId) {
        HistoryResult history = fetchHistoryIds(accessToken, lastHistoryId);
        List<MailSyncData> mails = fetchMessagesParallel(accessToken, history.ids());
        return new SyncResponseResult(mails, history.historyId(), null);
    }

    private HistoryResult fetchHistoryIds(String accessToken, String startHistoryId) {
        List<String> ids = new ArrayList<>();
        String latestHistoryId = startHistoryId;
        String pageToken = null;

        do {
            String uri = GMAIL_API_BASE + "/history?startHistoryId=" + startHistoryId
                    + "&historyTypes=messageAdded&maxResults=" + PAGE_SIZE;
            if (pageToken != null) uri += "&pageToken=" + pageToken;

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
                            ids.add(added.path("message").path("id").asText());
                        }
                    }
                }
            }

            pageToken = response.has("nextPageToken") ? response.get("nextPageToken").asText() : null;
        } while (pageToken != null);

        return new HistoryResult(ids, latestHistoryId);
    }

    private record HistoryResult(List<String> ids, String historyId) {}

    // -------------------------------------------------------------------------
    // 내부 - 전체 페이지 ID 수집 (순차 페이지네이션)
    // -------------------------------------------------------------------------

    private List<String> collectAllPageIds(String accessToken, String startPageToken) {
        List<String> ids = new ArrayList<>();
        String cursor = startPageToken;

        do {
            String uri = GMAIL_API_BASE + "/messages?maxResults=" + PAGE_SIZE + "&fields=messages/id,nextPageToken";
            if (cursor != null) uri += "&pageToken=" + cursor;

            JsonNode response = restClient.get()
                    .uri(uri)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.has("messages")) break;

            response.get("messages").forEach(msg -> ids.add(msg.get("id").asText()));
            log.info("[Gmail ID 수집] 누적 {}개", ids.size());

            cursor = response.has("nextPageToken") ? response.get("nextPageToken").asText() : null;
        } while (cursor != null);

        return ids;
    }

    // -------------------------------------------------------------------------
    // 내부 - 메시지 병렬 조회 (가상 스레드 + 세마포어 + 429 재시도)
    // -------------------------------------------------------------------------

    private List<MailSyncData> fetchMessagesParallel(String accessToken, List<String> ids) {
        if (ids.isEmpty()) return List.of();
        long start = System.currentTimeMillis();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<MailSyncData>> futures = ids.stream()
                    .map(id -> CompletableFuture.supplyAsync(
                            () -> fetchSingleMessageWithRetry(accessToken, id), executor))
                    .toList();

            List<MailSyncData> result = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .toList();

            log.info("[Gmail] 조회 완료 - 소요: {}ms", System.currentTimeMillis() - start);
            return result;
        }
    }

    private MailSyncData fetchSingleMessageWithRetry(String accessToken, String messageId) {
        int maxRetries = 5;
        long delay = 100;

        for (int i = 0; i <= maxRetries; i++) {
            RATE_LIMITER.acquireUninterruptibly();
            try {
                JsonNode message = restClient.get()
                        .uri(GMAIL_API_BASE + "/messages/" + messageId
                                + "?format=metadata&metadataHeaders=Subject&metadataHeaders=From&fields=id,snippet,internalDate,payload/headers")
                        .header("Authorization", "Bearer " + accessToken)
                        .retrieve()
                        .body(JsonNode.class);
                return toMailSyncData(message);
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                // 429(rate limit) 또는 5xx(서버 일시 오류)는 재시도
                boolean retryable = (status == 429 || status >= 500);
                if (retryable && i < maxRetries) {
                    log.debug("[재시도 {}/{}] id: {}, status: {}", i + 1, maxRetries, messageId, status);
                    sleep(delay);
                    delay = Math.min(delay * 2, 5000);
                } else {
                    log.warn("[유실] 메시지 조회 실패 - id: {}, status: {}", messageId, status);
                    return null;
                }
            } catch (Exception e) {
                // 네트워크 오류 등 일시적 장애도 재시도
                if (i < maxRetries) {
                    log.debug("[재시도 {}/{}] id: {}, error: {}", i + 1, maxRetries, messageId, e.getMessage());
                    sleep(delay);
                    delay = Math.min(delay * 2, 5000);
                } else {
                    log.warn("[유실] 메시지 조회 실패 - id: {}, error: {}", messageId, e.getMessage());
                    return null;
                }
            } finally {
                RATE_LIMITER.release();
            }
        }
        return null;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private MailSyncData toMailSyncData(JsonNode message) {
        String messageId = message != null ? message.path("id").asText("") : "";
        String subject = "";
        String from = "";

        if (message != null && message.has("payload")) {
            JsonNode payload = message.get("payload");
            if (payload.has("headers")) {
                for (JsonNode header : payload.get("headers")) {
                    switch (header.get("name").asText()) {
                        case "Subject" -> subject = header.get("value").asText();
                        case "From" -> from = header.get("value").asText();
                    }
                }
            }
        }

        String snippet = message != null ? message.path("snippet").asText("") : "";
        long internalDate = message != null ? message.path("internalDate").asLong(0) : 0;
        LocalDateTime receivedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(internalDate), ZoneId.systemDefault());

        String[] sender = parseSender(from);
        return new MailSyncData(messageId, subject, sender[0], sender[1], snippet, null, receivedAt, "GMAIL");
    }

    // -------------------------------------------------------------------------
    // MailBodyPort
    // -------------------------------------------------------------------------

    @Override
    public String fetchMailBody(MailAccount account, String credential, String externalMessageId) {
        JsonNode message = restClient.get()
                .uri(GMAIL_API_BASE + "/messages/" + externalMessageId + "?format=full")
                .header("Authorization", "Bearer " + credential)
                .retrieve()
                .body(JsonNode.class);
        if (message == null) return "";

        JsonNode payload = message.get("payload");
        Map<String, String> cidMap = new HashMap<>();
        resolveInlineImages(credential, externalMessageId, payload, cidMap);

        String body = extractBody(payload);
        for (Map.Entry<String, String> entry : cidMap.entrySet()) {
            body = body.replace("cid:" + entry.getKey(), entry.getValue());
        }
        return body;
    }

    private void resolveInlineImages(String accessToken, String messageId, JsonNode payload, Map<String, String> cidMap) {
        if (payload == null) return;

        String mimeType = payload.path("mimeType").asText("");
        if (mimeType.startsWith("image/")) {
            String contentId = null;
            if (payload.has("headers")) {
                for (JsonNode header : payload.get("headers")) {
                    if ("Content-ID".equalsIgnoreCase(header.path("name").asText())) {
                        contentId = header.path("value").asText().trim().replaceAll("^<|>$", "");
                    }
                }
            }
            if (contentId != null) {
                String data = payload.path("body").path("data").asText("");
                if (!data.isEmpty()) {
                    cidMap.put(contentId, "data:" + mimeType + ";base64," + data);
                } else {
                    String attachmentId = payload.path("body").path("attachmentId").asText("");
                    if (!attachmentId.isEmpty()) {
                        try {
                            JsonNode res = restClient.get()
                                    .uri(GMAIL_API_BASE + "/messages/" + messageId + "/attachments/" + attachmentId)
                                    .header("Authorization", "Bearer " + accessToken)
                                    .retrieve()
                                    .body(JsonNode.class);
                            String attachData = res != null ? res.path("data").asText("") : "";
                            if (!attachData.isEmpty()) cidMap.put(contentId, "data:" + mimeType + ";base64," + attachData);
                        } catch (Exception e) {
                            log.warn("인라인 이미지 조회 실패 - messageId: {}, attachmentId: {}", messageId, attachmentId);
                        }
                    }
                }
            }
        }

        if (payload.has("parts")) {
            for (JsonNode part : payload.get("parts")) {
                resolveInlineImages(accessToken, messageId, part, cidMap);
            }
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
                if (partMime.equals("text/html")) return decodeBase64(part.path("body").path("data").asText(""));
                if (partMime.equals("text/plain")) plain = decodeBase64(part.path("body").path("data").asText(""));
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
        try {
            InternetAddress[] addrs = InternetAddress.parseHeader(from, false);
            if (addrs.length > 0) {
                String email = addrs[0].getAddress() != null ? addrs[0].getAddress() : from.trim();
                String name = addrs[0].getPersonal() != null ? addrs[0].getPersonal() : "";
                return new String[]{name, email};
            }
        } catch (Exception ignored) {}
        if (from.contains("<")) {
            return new String[]{
                from.substring(0, from.indexOf("<")).trim().replace("\"", ""),
                from.substring(from.indexOf("<") + 1, from.indexOf(">")).trim()
            };
        }
        return new String[]{"", from.trim()};
    }
}
