package com.example.mailmoa.mail.infrastructure.gmail;

import com.example.mailmoa.mail.application.dto.MailSyncData;
import com.example.mailmoa.mail.application.dto.SyncResponseResult;
import com.example.mailmoa.mail.application.dto.TokenRefreshResult;
import com.example.mailmoa.mail.application.port.GmailPort;
import com.example.mailmoa.mail.application.port.MailBodyPort;
import com.example.mailmoa.mail.application.port.MailSyncPort;
import com.example.mailmoa.mailaccount.domain.model.MailAccount;
import com.example.mailmoa.mailaccount.domain.model.MailProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class GmailAdapter implements GmailPort, MailSyncPort, MailBodyPort {

    private static final String GMAIL_API_BASE = "https://gmail.googleapis.com/gmail/v1/users/me";
    private static final String GMAIL_BATCH_URL = "https://www.googleapis.com/batch/gmail/v1";
    private static final int PAGE_SIZE = 100;
    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRY = 3;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    @Autowired
    @Qualifier("mailSyncExecutor")
    private Executor mailSyncExecutor;

    // -------------------------------------------------------------------------
    // MailSyncPort 구현
    // -------------------------------------------------------------------------

    @Override
    public MailProvider getSupportedProvider() {
        return MailProvider.GMAIL;
    }

    @Override
    public SyncResponseResult fetchMails(MailAccount account, String credential) {
        return fetchMails(credential, account.getLastHistoryId());
    }

    // -------------------------------------------------------------------------
    // GmailPort 구현
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

    @Override
    public SyncResponseResult fetchMails(String accessToken, String lastHistoryId) {
        if (lastHistoryId == null) {
            return fullSync(accessToken);
        }
        return incrementalSync(accessToken, lastHistoryId);
    }

    private SyncResponseResult fullSync(String accessToken) {
        List<String> messageIds = fetchMessageIds(accessToken);
        log.info("전체 동기화 시작 - 메일 수: {}", messageIds.size());

        List<MailSyncData> mails = fetchMessages(accessToken, messageIds);

        JsonNode profile = restClient.get()
                .uri(GMAIL_API_BASE + "/profile")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class);
        String historyId = profile != null ? profile.path("historyId").asText(null) : null;

        return new SyncResponseResult(mails, historyId);
    }

    private SyncResponseResult incrementalSync(String accessToken, String lastHistoryId) {
        List<String> newMessageIds = new ArrayList<>();
        String pageToken = null;
        String latestHistoryId = lastHistoryId;

        do {
            String uri = GMAIL_API_BASE + "/history?startHistoryId=" + lastHistoryId
                    + "&historyTypes=messageAdded&maxResults=" + PAGE_SIZE;
            if (pageToken != null) uri += "&pageToken=" + pageToken;

            JsonNode response = restClient.get()
                    .uri(uri)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) break;

            if (response.has("historyId")) latestHistoryId = response.get("historyId").asText();

            if (response.has("history")) {
                for (JsonNode history : response.get("history")) {
                    if (history.has("messagesAdded")) {
                        for (JsonNode added : history.get("messagesAdded")) {
                            newMessageIds.add(added.path("message").path("id").asText());
                        }
                    }
                }
            }

            pageToken = response.has("nextPageToken") ? response.get("nextPageToken").asText() : null;
        } while (pageToken != null);

        return new SyncResponseResult(fetchMessages(accessToken, newMessageIds), latestHistoryId);
    }


    private List<String> fetchMessageIds(String accessToken) {
        List<String> ids = new ArrayList<>();
        String pageToken = null;
        int page = 1;

        do {
            String uri = GMAIL_API_BASE + "/messages?maxResults=" + PAGE_SIZE;
            if (pageToken != null) uri += "&pageToken=" + pageToken;

            JsonNode response = restClient.get()
                    .uri(uri)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) break;
            if (response.has("messages")) {
                int before = ids.size();
                for (JsonNode msg : response.get("messages")) ids.add(msg.get("id").asText());
                log.info("[전체 동기화] 페이지 {} - {}개 수집 (누적: {}개)", page, ids.size() - before, ids.size());
            }

            pageToken = response.has("nextPageToken") ? response.get("nextPageToken").asText() : null;
            page++;
        } while (pageToken != null);

        log.info("[전체 동기화] 메일 ID 수집 완료 - 총 {}개", ids.size());
        return ids;
    }

    private List<MailSyncData> fetchMessages(String accessToken, List<String> messageIds) {
        if (messageIds.isEmpty()) return List.of();

        List<List<String>> chunks = partition(messageIds, BATCH_SIZE);
        log.info("배치 병렬 조회 시작 - 총 메일: {}, 배치 수: {}", messageIds.size(), chunks.size());

        List<CompletableFuture<List<MailSyncData>>> futures = chunks.stream()
                .map(chunk -> CompletableFuture.supplyAsync(
                        () -> fetchMessagesBatchWithRetry(accessToken, chunk),
                        mailSyncExecutor))
                .toList();

        List<MailSyncData> allMails = futures.stream()
                .flatMap(f -> f.join().stream())
                .toList();

        log.info("[fetchMessages] 요청 {}개 → 수집 {}개 ({}개 누락)",
                messageIds.size(), allMails.size(), messageIds.size() - allMails.size());
        return allMails;
    }

    private List<MailSyncData> fetchMessagesBatchWithRetry(String accessToken, List<String> chunk) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                return fetchMessagesBatch(accessToken, chunk);
            } catch (Exception e) {
                if (attempt == MAX_RETRY) {
                    log.warn("배치 최종 실패 ({}회 시도) - 청크 크기: {}, error: {}", MAX_RETRY, chunk.size(), e.getMessage());
                    return List.of();
                }
                log.warn("배치 실패 ({}/{}) - 재시도, error: {}", attempt, MAX_RETRY, e.getMessage());
            }
        }
        return List.of();
    }

    private List<MailSyncData> fetchMessagesBatch(String accessToken, List<String> messageIds) {
        // --- 요청 구성 ---
        String boundary = "batch_" + System.nanoTime();
        StringBuilder sb = new StringBuilder();
        for (String id : messageIds) {
            sb.append("--").append(boundary).append("\r\n")
              .append("Content-Type: application/http\r\n")
              .append("Content-ID: ").append(id).append("\r\n\r\n")
              .append("GET /gmail/v1/users/me/messages/").append(id).append("?format=metadata&metadataHeaders=Subject&metadataHeaders=From\r\n\r\n");
        }
        sb.append("--").append(boundary).append("--");

        ResponseEntity<byte[]> response = restClient.post()
                .uri(GMAIL_BATCH_URL)
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "multipart/mixed; boundary=" + boundary)
                .body(sb.toString())
                .retrieve()
                .toEntity(byte[].class);

        // --- 응답 파싱 ---
        String responseBody = response.getBody() != null
                ? new String(response.getBody(), StandardCharsets.UTF_8) : null;
        String contentType = response.getHeaders().getFirst("Content-Type");
        if (responseBody == null || contentType == null) return List.of();

        // Content-Type 헤더에서 응답 boundary 추출
        String responseBoundary = null;
        for (String token : contentType.split(";")) {
            token = token.trim();
            if (token.startsWith("boundary=")) {
                responseBoundary = token.substring("boundary=".length()).replace("\"", "");
                break;
            }
        }
        if (responseBoundary == null) return List.of();

        // multipart 파트별로 JSON 추출 → MailSyncData 변환
        List<MailSyncData> result = new ArrayList<>();
        List<String> retryIds = new ArrayList<>();

        for (String part : responseBody.split("--" + Pattern.quote(responseBoundary))) {
            int httpStart = part.indexOf("HTTP/1.1");
            if (httpStart < 0) continue;

            // Content-ID 헤더에서 메시지 ID 추출 (429 재시도용)
            String contentId = null;
            for (String line : part.substring(0, httpStart).split("\r?\n")) {
                if (line.toLowerCase().startsWith("content-id:")) {
                    contentId = line.substring("content-id:".length()).trim()
                            .replaceAll("(?i)^response-", "").replaceAll("[<>]", "");
                    break;
                }
            }

            String httpPart = part.substring(httpStart);
            String statusLine = httpPart.lines().findFirst().orElse("");

            if (statusLine.contains("429")) {
                if (contentId != null) retryIds.add(contentId);
                continue;
            }
            if (!statusLine.contains("200")) {
                log.warn("[유실] 배치 응답 비정상 상태: {}", statusLine.trim());
                continue;
            }

            int bodyStart = httpPart.indexOf("\r\n\r\n");
            bodyStart = bodyStart >= 0 ? bodyStart + 4 : httpPart.indexOf("\n\n") + 2;
            if (bodyStart < 2) continue;

            String json = httpPart.substring(bodyStart).trim();
            if (json.isEmpty() || json.startsWith("--")) continue;

            try {
                JsonNode message = objectMapper.readTree(json);
                result.add(toMailSyncData(message));
            } catch (Exception e) {
                log.warn("배치 응답 파싱 실패: {}", e.getMessage());
            }
        }

        // 429 메시지 재시도 (개별 조회)
        if (!retryIds.isEmpty()) {
            log.info("[재시도] 429 메시지 {}개 개별 조회", retryIds.size());
            for (String id : retryIds) {
                try {
                    MailSyncData mail = fetchMessage(accessToken, id);
                    if (mail != null) result.add(mail);
                } catch (Exception e) {
                    log.warn("[유실] 재시도 실패 - messageId: {}, error: {}", id, e.getMessage());
                }
            }
        }

        return result;
    }

    private MailSyncData fetchMessage(String accessToken, String messageId) {
        JsonNode message = restClient.get()
                .uri(GMAIL_API_BASE + "/messages/" + messageId + "?format=metadata&metadataHeaders=Subject&metadataHeaders=From")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class);
        return toMailSyncData(message);
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

    // 인라인 이미지를 Base64 Data URI로 교체
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

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }
}
