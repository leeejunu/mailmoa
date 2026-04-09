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
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;
import reactor.util.retry.Retry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import jakarta.mail.internet.InternetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
public class GmailAdapter implements GmailPort, GmailWatchPort, MailSyncPort, MailBodyPort {

    private static final String GMAIL_API_BASE = "https://gmail.googleapis.com/gmail/v1/users/me";
    private static final int PAGE_SIZE = 100;
    // 동시 요청 수: 10개 × 5 quota units = 50 units/순간, 요청당 ~200ms → ~250 units/초
    private static final int CONCURRENCY = 200;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    private final ObjectMapper objectMapper;
    private final WebClient webClient = WebClient.create();

    // -------------------------------------------------------------------------
    // MailSyncPort
    // -------------------------------------------------------------------------

    @Override
    public MailProvider getSupportedProvider() {
        return MailProvider.GMAIL;
    }

    @Override
    public Mono<SyncResponseResult> fetchMails(MailAccount account, String credential) {
        if (account.getLastHistoryId() == null) {
            return fullSyncReactive(credential);
        }
        return incrementalSyncReactive(credential, account.getLastHistoryId());
    }

    @Override
    public Flux<MailSyncData> fetchRemaining(MailAccount account, String credential, String continuationToken) {
        return fetchMessagesStreaming(credential, collectAllPageIds(credential, continuationToken));
    }

    // -------------------------------------------------------------------------
    // GmailWatchPort
    // -------------------------------------------------------------------------

    @Override
    public Mono<Void> setupWatch(String accessToken, String topicName) {
        Map<String, Object> body = Map.of(
                "topicName", topicName,
                "labelIds", List.of("INBOX")
        );
        return webClient.post()
                .uri(GMAIL_API_BASE + "/watch")
                .header("Authorization", "Bearer " + accessToken)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    // -------------------------------------------------------------------------
    // GmailPort
    // -------------------------------------------------------------------------

    @Override
    public TokenRefreshResult refreshAccessToken(String refreshToken) {
        String body = "grant_type=refresh_token&refresh_token=" + refreshToken
                + "&client_id=" + clientId + "&client_secret=" + clientSecret;

        JsonNode response = webClient.post()
                .uri("https://oauth2.googleapis.com/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        String accessToken = response.get("access_token").asText();
        long expiresIn = response.path("expires_in").asLong(3600);
        return new TokenRefreshResult(accessToken, expiresIn);
    }

    @Override
    public void trashMail(String accessToken, String externalMessageId) {
        webClient.post()
                .uri(GMAIL_API_BASE + "/messages/{id}/trash", externalMessageId)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    // -------------------------------------------------------------------------
    // 내부 - full sync
    // -------------------------------------------------------------------------

    private Mono<SyncResponseResult> fullSyncReactive(String accessToken) {
        // profile 조회와 전체 ID 수집을 동시에 시작 (백프레셔 없이)
        Mono<String> historyIdMono = webClient.get()
                .uri(GMAIL_API_BASE + "/profile")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(p -> p != null ? p.path("historyId").asText(null) : null);

        Mono<List<String>> allIdsMono = collectAllPageIds(accessToken, null).collectList();

        return Mono.zip(historyIdMono, allIdsMono)
                .flatMap(tuple -> {
                    String historyId = tuple.getT1();
                    List<String> allIds = tuple.getT2();
                    log.info("[Gmail ID 수집 완료] 총 {}개", allIds.size());
                    return fetchMessagesStreaming(accessToken, Flux.fromIterable(allIds))
                            .collectList()
                            .map(mails -> {
                                log.info("[Gmail 전체 동기화] {}개 완료", mails.size());
                                return new SyncResponseResult(mails, historyId, null);
                            });
                });
    }

    // -------------------------------------------------------------------------
    // 내부 - incremental sync
    // -------------------------------------------------------------------------

    private Mono<SyncResponseResult> incrementalSyncReactive(String accessToken, String lastHistoryId) {
        return fetchHistoryIds(accessToken, lastHistoryId, null, new ArrayList<>(), lastHistoryId)
                .flatMap(pair -> fetchMessagesStreaming(accessToken, Flux.fromIterable(pair.getT1()))
                        .collectList()
                        .map(mails -> new SyncResponseResult(mails, pair.getT2(), null)));
    }

    private Mono<reactor.util.function.Tuple2<List<String>, String>> fetchHistoryIds(
            String accessToken, String startHistoryId, String pageToken,
            List<String> accumulated, String currentHistoryId) {

        String uri = GMAIL_API_BASE + "/history?startHistoryId=" + startHistoryId
                + "&historyTypes=messageAdded&maxResults=" + PAGE_SIZE;
        if (pageToken != null) uri += "&pageToken=" + pageToken;

        return webClient.get()
                .uri(uri)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(response -> {
                    if (response == null) {
                        return Mono.just(Tuples.of(accumulated, currentHistoryId));
                    }

                    String latestHistoryId = response.has("historyId")
                            ? response.get("historyId").asText()
                            : currentHistoryId;

                    if (response.has("history")) {
                        for (JsonNode history : response.get("history")) {
                            if (history.has("messagesAdded")) {
                                for (JsonNode added : history.get("messagesAdded")) {
                                    accumulated.add(added.path("message").path("id").asText());
                                }
                            }
                        }
                    }

                    String nextPage = response.has("nextPageToken") ? response.get("nextPageToken").asText() : null;
                    if (nextPage != null) {
                        return fetchHistoryIds(accessToken, startHistoryId, nextPage, accumulated, latestHistoryId);
                    }
                    return Mono.just(Tuples.of(accumulated, latestHistoryId));
                });
    }

    // -------------------------------------------------------------------------
    // 내부 - 전체 페이지 ID 스트리밍 (첫 페이지부터 순차 수집)
    // -------------------------------------------------------------------------

    private Flux<String> collectAllPageIds(String accessToken, String pageToken) {
        String uri = GMAIL_API_BASE + "/messages?maxResults=" + PAGE_SIZE + "&fields=messages/id,nextPageToken";
        if (pageToken != null) uri += "&pageToken=" + pageToken;

        return webClient.get()
                .uri(uri)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMapMany(response -> {
                    if (response == null || !response.has("messages")) return Flux.empty();

                    List<String> ids = new ArrayList<>();
                    response.get("messages").forEach(msg -> ids.add(msg.get("id").asText()));
                    log.info("[Gmail ID 수집] {}개", ids.size());

                    String nextPage = response.has("nextPageToken") ? response.get("nextPageToken").asText() : null;
                    Flux<String> current = Flux.fromIterable(ids);

                    if (nextPage != null) {
                        return current.concatWith(collectAllPageIds(accessToken, nextPage));
                    }
                    return current;
                });
    }

    // -------------------------------------------------------------------------
    // 내부 - 메시지 조회 (ID Flux를 받아 동시 요청 + 429 재시도)
    // -------------------------------------------------------------------------

    private Flux<MailSyncData> fetchMessagesStreaming(String accessToken, Flux<String> idFlux) {
        long start = System.currentTimeMillis();
        return idFlux
                .flatMap(id -> fetchSingleMessage(accessToken, id)
                        .retryWhen(Retry.backoff(5, Duration.ofMillis(100))
                                .maxBackoff(Duration.ofSeconds(5))
                                .filter(ex -> ex instanceof WebClientResponseException wex
                                        && wex.getStatusCode().value() == 429))
                        .onErrorResume(ex -> {
                            log.warn("[유실] 메시지 조회 실패 - id: {}", id);
                            return Mono.empty();
                        }),
                        CONCURRENCY)
                .doOnComplete(() -> log.info("[Gmail] 조회 완료 - 소요: {}ms", System.currentTimeMillis() - start));
    }

    private Mono<MailSyncData> fetchSingleMessage(String accessToken, String messageId) {
        return webClient.get()
                .uri(GMAIL_API_BASE + "/messages/" + messageId
                        + "?format=metadata&metadataHeaders=Subject&metadataHeaders=From&fields=id,snippet,internalDate,payload/headers")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::toMailSyncData);
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
        JsonNode message = webClient.get()
                .uri(GMAIL_API_BASE + "/messages/" + externalMessageId + "?format=full")
                .header("Authorization", "Bearer " + credential)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
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
                            JsonNode res = webClient.get()
                                    .uri(GMAIL_API_BASE + "/messages/" + messageId + "/attachments/" + attachmentId)
                                    .header("Authorization", "Bearer " + accessToken)
                                    .retrieve()
                                    .bodyToMono(JsonNode.class)
                                    .block();
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
