package com.mail.moa.email.service.provider;

import com.mail.moa.domain.EmailAccount;
import com.mail.moa.emailAccount.EmailAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class GmailProvider implements MailProvider{

    private final EmailAccountRepository emailAccountRepository;


    @Override
    public boolean supports(String provider) {
        return "GMAIL".equalsIgnoreCase(provider);
    }

    @Override
    public List<Map<String, Object>> fetchMails(String email) {
        EmailAccount emailAccount = emailAccountRepository.findByEmailAddress(email)
                .orElseThrow(() -> new IllegalStateException("해당 이메일 계정을 찾을 수 없습니다: " + email));

        // 2. RestClient 설정 (Base URL과 Authorization 헤더 미리 세팅)
        RestClient restClient = createGoogleRestClient(emailAccount);

        // 3. 메일 리스트 조회
        JsonNode findMails = getMails(restClient);

        log.info("fetch mails: {}", findMails);

        // 메시지가 없으면 빈 리스트 반환
        if (!hasMessages(findMails)) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> mailList = new ArrayList<>();
        JsonNode messages = findMails.get("messages");

        // 조회된 ID들을 반복문 돌면서 상세 정보 긁어오기
        for (JsonNode messageNode : messages) {
            String messageId = messageNode.get("id").asText();

            // 1. 상세 조회
            JsonNode findMail = getMail(restClient, messageId);

            // 2. 파싱 (제목, 보낸이 등)
            Map<String, Object> mailData = parseMessage(messageId, findMail);

            // 3. 본문 추출 및 디코딩
            String encodedBody = encodedBody(findMail);
            decodedBody(encodedBody, mailData);

            // 4. 리스트에 추가
            mailList.add(mailData);
        }

        return mailList;
    }

    private Map<String, Object> parseMessage(String messageId, JsonNode findMail) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", messageId);
        result.put("snippet", findMail.get("snippet").asText());

        // 헤더 정보 파싱 (Subject, From)
        JsonNode headers = findMail.get("payload").get("headers");
        for (JsonNode header : headers) {
            String name = header.get("name").toString();
            if (name.equalsIgnoreCase("Subject")) {
                result.put("subject", header.get("value").asText());
            }
            if (name.equalsIgnoreCase("From")) {
                result.put("from", header.get("value").asText());
            }
        }
        return result;
    }

    private RestClient createGoogleRestClient(EmailAccount emailAccount) {
        RestClient restClient = RestClient.builder()
                .baseUrl("https://www.googleapis.com/gmail/v1")
                .defaultHeader("Authorization", "Bearer " + emailAccount.getAccessToken())
                .build();
        return restClient;
    }

    private void decodedBody(String encodedBody, Map<String, Object> result) {
        if (!encodedBody.isEmpty()) {
            // Gmail 전용 Base64 URL Safe 디코딩
            byte[] decodedBytes = Base64.getUrlDecoder().decode(encodedBody);
            String decodedBody = new String(decodedBytes, StandardCharsets.UTF_8);
            result.put("body", decodedBody);
        }
    }

    private String encodedBody(JsonNode findMail) {
        String encodedBody = "";
        JsonNode payload = findMail.get("payload");

        if (payload.get("body").has("data")) {
            encodedBody = payload.get("body").get("data").asText();
        } else if (payload.has("parts")) {
            // parts가 있는 경우 첫 번째 파트에서 데이터를 가져옴
            for (JsonNode part : payload.get("parts")) {
                if (part.has("body") && part.get("body").has("data")) {
                    encodedBody = part.get("body").get("data").asText();
                    break;
                }
            }
        }
        return encodedBody;
    }

    private JsonNode getMail(RestClient restClient, String messageId) {
        JsonNode detailResponse = restClient.get()
                .uri("/users/me/messages/" + messageId)
                .retrieve()
                .body(JsonNode.class);
        return detailResponse;
    }

    private JsonNode getMails(RestClient restClient) {
        return restClient.get()
                .uri("/users/me/messages?maxResults=5")
                .retrieve()
                .body(JsonNode.class);
    }

    private boolean hasMessages(JsonNode listResponse) {
        return listResponse != null && listResponse.has("messages");
    }
}
