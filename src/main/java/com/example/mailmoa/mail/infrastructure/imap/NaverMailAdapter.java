package com.example.mailmoa.mail.infrastructure.imap;

import com.example.mailmoa.mail.application.dto.MailSyncData;
import com.example.mailmoa.mail.application.dto.SyncResponseResult;
import com.example.mailmoa.mail.application.port.MailBodyPort;
import com.example.mailmoa.mail.application.port.MailSyncPort;
import com.example.mailmoa.mail.application.port.NaverMailPort;
import com.example.mailmoa.mailaccount.domain.model.MailAccount;
import com.example.mailmoa.mailaccount.domain.model.MailProvider;
import com.example.mailmoa.mailaccount.application.exception.NaverAuthException;
import jakarta.mail.*;
import jakarta.mail.FetchProfile;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeUtility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class NaverMailAdapter implements NaverMailPort, MailSyncPort, MailBodyPort {

    private static final int CHUNK_SIZE = 100;

    @Override
    public MailProvider getSupportedProvider() {
        return MailProvider.NAVER;
    }

    @Override
    public SyncResponseResult fetchMails(MailAccount account, String credential) {
        String lastUid = account.getLastHistoryId();
        if (lastUid == null) {
            return fetchInitial(account.getEmailAddress(), credential);
        }
        return fetchIncremental(account.getEmailAddress(), credential, lastUid);
    }

    @Override
    public List<MailSyncData> fetchRemaining(MailAccount account, String credential, String continuationToken) {
        int remaining = Integer.parseInt(continuationToken);
        String email = account.getEmailAddress();

        List<int[]> chunks = new ArrayList<>();
        for (int to = remaining; to >= 1; to -= CHUNK_SIZE) {
            int from = Math.max(1, to - CHUNK_SIZE + 1);
            chunks.add(new int[]{from, to});
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<List<MailSyncData>>> futures = chunks.stream()
                    .map(range -> CompletableFuture.supplyAsync(
                            () -> fetchRange(email, credential, range[0], range[1]), executor))
                    .toList();
            return futures.stream()
                    .flatMap(f -> f.join().stream())
                    .toList();
        }
    }

    private static final String IMAP_HOST = "imap.naver.com";
    private static final int IMAP_PORT = 993;

    @Override
    public void testConnection(String email, String password) {
        Store store = null;
        try {
            store = openStore(email, password);
        } catch (AuthenticationFailedException e) {
            throw new NaverAuthException("아이디 또는 비밀번호가 올바르지 않습니다.");
        } catch (Exception e) {
            throw new NaverAuthException("네이버 메일 연결에 실패했습니다. IMAP 사용 설정을 확인하세요.");
        } finally {
            closeStore(store);
        }
    }

    private SyncResponseResult fetchInitial(String email, String password) {
        Store store = null;
        Folder inbox = null;
        try {
            store = openStore(email, password);
            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            int count = inbox.getMessageCount();
            if (count == 0) return new SyncResponseResult(List.of(), null, null);

            int from = Math.max(1, count - 99);
            Message[] messages = inbox.getMessages(from, count);

            FetchProfile fp = new FetchProfile();
            fp.add(FetchProfile.Item.ENVELOPE);
            fp.add(UIDFolder.FetchProfileItem.UID);
            fp.add("From");
            fp.add("Subject");
            inbox.fetch(messages, fp);

            UIDFolder uidFolder = (UIDFolder) inbox;
            List<MailSyncData> mails = new ArrayList<>();
            long maxUid = 0;

            for (Message message : messages) {
                try {
                    long uid = uidFolder.getUID(message);
                    maxUid = Math.max(maxUid, uid);
                    MailSyncData data = toMailSyncData(message, uid);
                    if (data != null) mails.add(data);
                } catch (Exception e) {
                    log.warn("Naver message parse failed: {}", e.getMessage());
                }
            }

            String nextPageToken = from > 1 ? String.valueOf(from - 1) : null;
            return new SyncResponseResult(mails, maxUid > 0 ? String.valueOf(maxUid) : null, nextPageToken);
        } catch (Exception e) {
            log.error("Naver IMAP fetch failed: {}", e.getMessage());
            throw new RuntimeException("Naver 메일 동기화 실패", e);
        } finally {
            closeFolder(inbox);
            closeStore(store);
        }
    }

    private SyncResponseResult fetchIncremental(String email, String password, String lastUid) {
        Store store = null;
        Folder inbox = null;
        try {
            store = openStore(email, password);
            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            UIDFolder uidFolder = (UIDFolder) inbox;
            long lastUidLong = Long.parseLong(lastUid);
            Message[] messages = uidFolder.getMessagesByUID(lastUidLong + 1, UIDFolder.LASTUID);

            if (messages.length == 0) return new SyncResponseResult(List.of(), lastUid, null);

            FetchProfile fp = new FetchProfile();
            fp.add(FetchProfile.Item.ENVELOPE);
            fp.add(UIDFolder.FetchProfileItem.UID);
            fp.add("From");
            fp.add("Subject");
            inbox.fetch(messages, fp);

            List<MailSyncData> mails = new ArrayList<>();
            long maxUid = lastUidLong;

            for (Message message : messages) {
                try {
                    long uid = uidFolder.getUID(message);
                    maxUid = Math.max(maxUid, uid);
                    MailSyncData data = toMailSyncData(message, uid);
                    if (data != null) mails.add(data);
                } catch (Exception e) {
                    log.warn("Naver message parse failed: {}", e.getMessage());
                }
            }

            return new SyncResponseResult(mails, String.valueOf(maxUid), null);
        } catch (Exception e) {
            log.error("Naver IMAP incremental fetch failed: {}", e.getMessage());
            throw new RuntimeException("Naver 메일 동기화 실패", e);
        } finally {
            closeFolder(inbox);
            closeStore(store);
        }
    }

    @Override
    public void trashMail(String email, String password, String uid) {
        Store store = null;
        Folder inbox = null;
        try {
            store = openStore(email, password);
            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            UIDFolder uidFolder = (UIDFolder) inbox;
            Message message = uidFolder.getMessageByUID(Long.parseLong(uid));
            if (message == null) return;

            Folder trash = findTrashFolder(store);
            if (trash != null) {
                inbox.copyMessages(new Message[]{message}, trash);
            }
            message.setFlag(Flags.Flag.DELETED, true);
            inbox.expunge();
        } catch (Exception e) {
            log.error("Naver IMAP trash failed: {}", e.getMessage());
            throw new RuntimeException("Naver 메일 삭제 실패", e);
        } finally {
            closeFolder(inbox);
            closeStore(store);
        }
    }

    private Store openStore(String email, String password) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", IMAP_HOST);
        props.put("mail.imaps.port", String.valueOf(IMAP_PORT));
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.timeout", "15000");
        props.put("mail.imaps.connectiontimeout", "10000");

        Session session = Session.getInstance(props);
        Store store = session.getStore("imaps");
        String username = email.contains("@") ? email.split("@")[0] : email;
        store.connect(IMAP_HOST, IMAP_PORT, username, password);
        return store;
    }

    private Folder findTrashFolder(Store store) {
        for (String name : new String[]{"휴지통", "Trash", "Deleted Messages", "Deleted Items"}) {
            try {
                Folder f = store.getFolder(name);
                if (f.exists()) return f;
            } catch (MessagingException ignored) {}
        }
        return null;
    }

    @Override
    public List<MailSyncData> fetchRange(String email, String password, int from, int to) {
        Store store = null;
        Folder inbox = null;
        try {
            store = openStore(email, password);
            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            int count = inbox.getMessageCount();
            int actualTo = Math.min(to, count);
            if (from > actualTo) return List.of();

            Message[] messages = inbox.getMessages(from, actualTo);

            FetchProfile fp = new FetchProfile();
            fp.add(FetchProfile.Item.ENVELOPE);
            fp.add(UIDFolder.FetchProfileItem.UID);
            fp.add("From");
            fp.add("Subject");
            inbox.fetch(messages, fp);

            UIDFolder uidFolder = (UIDFolder) inbox;
            List<MailSyncData> mails = new ArrayList<>();
            for (Message message : messages) {
                try {
                    long uid = uidFolder.getUID(message);
                    MailSyncData data = toMailSyncData(message, uid);
                    if (data != null) mails.add(data);
                } catch (Exception e) {
                    log.warn("Naver fetchRange message parse failed: {}", e.getMessage());
                }
            }
            return mails;
        } catch (Exception e) {
            log.error("Naver IMAP fetchRange failed [{}-{}]: {}", from, to, e.getMessage());
            throw new RuntimeException("Naver 메일 범위 조회 실패", e);
        } finally {
            closeFolder(inbox);
            closeStore(store);
        }
    }

    @Override
    public String fetchMailBody(MailAccount account, String credential, String externalMessageId) {
        return fetchMailBody(account.getEmailAddress(), credential, externalMessageId);
    }

    public String fetchMailBody(String email, String password, String uid) {
        Store store = null;
        Folder inbox = null;
        try {
            store = openStore(email, password);
            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            UIDFolder uidFolder = (UIDFolder) inbox;
            Message message = uidFolder.getMessageByUID(Long.parseLong(uid));
            if (message == null) return "";
            return extractBody(message);
        } catch (Exception e) {
            log.error("Naver fetchMailBody failed uid={}: {}", uid, e.getMessage());
            return "";
        } finally {
            closeFolder(inbox);
            closeStore(store);
        }
    }

    private MailSyncData toMailSyncData(Message message, long uid) {
        try {
            String subject = "(제목 없음)";
            String[] subjectHeaders = message.getHeader("Subject");
            if (subjectHeaders != null && subjectHeaders.length > 0) {
                subject = MimeUtility.decodeText(subjectHeaders[0]);
            } else if (message.getSubject() != null) {
                subject = MimeUtility.decodeText(message.getSubject());
            }

            String senderName = "";
            String senderEmail = "";
            String[] fromHeaders = message.getHeader("From");
            if (fromHeaders != null && fromHeaders.length > 0) {
                InternetAddress[] addrs = InternetAddress.parseHeader(fromHeaders[0], false);
                if (addrs.length > 0) {
                    senderEmail = addrs[0].getAddress() != null ? addrs[0].getAddress() : "";
                    String personal = addrs[0].getPersonal();
                    senderName = personal != null ? personal : senderEmail;
                }
            }
            if (senderEmail.isEmpty()) {
                Address[] from = message.getFrom();
                if (from != null && from.length > 0 && from[0] instanceof InternetAddress addr) {
                    senderEmail = addr.getAddress() != null ? addr.getAddress() : "";
                    String personal = addr.getPersonal();
                    senderName = personal != null ? MimeUtility.decodeText(personal) : senderEmail;
                }
            }

            Date sentDate = message.getSentDate();
            LocalDateTime receivedAt = sentDate != null
                    ? sentDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                    : LocalDateTime.now();

            return new MailSyncData(String.valueOf(uid), subject, senderName, senderEmail,
                    "", "", receivedAt, "NAVER");
        } catch (Exception e) {
            log.warn("Failed to parse IMAP message uid={}: {}", uid, e.getMessage());
            return null;
        }
    }

    private String extractBody(Part part) throws Exception {
        if (part.isMimeType("text/html")) {
            return (String) part.getContent();
        }
        if (part.isMimeType("text/plain")) {
            return (String) part.getContent();
        }
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            String plain = "";
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                if (bp.isMimeType("text/html")) return extractBody(bp);
                if (bp.isMimeType("text/plain")) plain = extractBody(bp);
                if (bp.isMimeType("multipart/*")) {
                    String nested = extractBody(bp);
                    if (!nested.isEmpty()) return nested;
                }
            }
            return plain;
        }
        return "";
    }

    private void closeFolder(Folder folder) {
        if (folder != null && folder.isOpen()) {
            try { folder.close(false); } catch (Exception ignored) {}
        }
    }

    private void closeStore(Store store) {
        if (store != null) {
            try { store.close(); } catch (Exception ignored) {}
        }
    }
}
