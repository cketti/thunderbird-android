package com.fsck.k9.controller.legacy;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import android.content.Context;

import com.fsck.k9.Account;
import com.fsck.k9.mail.FetchProfile;
import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.Folder.FolderType;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.data.Message;
import com.fsck.k9.mail.data.MessageServerData;
import com.fsck.k9.mail.data.Part;
import com.fsck.k9.mail.message.MessageParser;
import com.fsck.k9.mailstore.LocalFolder;
import com.fsck.k9.mailstore.LocalMessage;
import com.fsck.k9.mailstore.LocalStore;
import com.fsck.k9.mailstore.StorageManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


/**
 * Test to determine if a {@link LegacyMessage} can be successfully saved to and retrieved from the database.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = "src/main/AndroidManifest.xml", sdk = 19)
public class LegacyStorageTest {
    private static final String CRLF = "\r\n";
    private static final String ACCOUNT_UUID = "00000000-0000-0000-0000-000000000000";
    private static final String TEST_FOLDER_NAME = "Test";
    private static final String MESSAGE_SERVER_ID = "23";


    private Context context;
    private LocalFolder testFolder;


    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.application;
        Account account = createFakeAccount();

        LocalStore localStore = account.getLocalStore();
        testFolder = localStore.getFolder(TEST_FOLDER_NAME);
        testFolder.create(FolderType.HOLDS_MESSAGES, 25);
    }

    @Test
    public void testSimpleMessage() throws Exception {
        String messageSource = "" +
                "From: alice@example.org" + CRLF +
                "To: bob@example.org" + CRLF +
                "Subject: Test" + CRLF +
                "Date: Wed, 21 Oct 2015 00:42:23 +0200" + CRLF +
                "Content-Type: text/plain" + CRLF +
                "Mime-Version: 1.0" + CRLF +
                CRLF +
                "This is a test";
        MessageServerData messageServerData = new MessageServerDataBuilder()
                .messageSource(messageSource)
                .addFlag(Flag.SEEN)
                .addFlag(Flag.ANSWERED)
                .build();

        LocalMessage localMessage = saveAndRestoreMessage(messageServerData);

        assertEquals(messageSource, writeLocalMessageToString(localMessage));
        assertEquals(flagSet(Flag.SEEN, Flag.ANSWERED), localMessage.getFlags());
    }

    @Test
    public void testMultipartMessage() throws Exception {
        String messageSource = "" +
                "From: <alice@example.org>" + CRLF +
                "To: Bob <bob@example.org>" + CRLF +
                "Subject: multipart test" + CRLF +
                "MIME-Version: 1.0" + CRLF +
                "Content-Type: multipart/alternative; boundary=--boundary" + CRLF +
                CRLF +
                "This is a message with multiple parts in MIME format." + CRLF +
                "----boundary" + CRLF +
                "Content-Type: text/plain" + CRLF +
                CRLF +
                "This is a plain text body." +
                CRLF +
                "----boundary" + CRLF +
                "Content-Type: text/html" + CRLF +
                "Content-Transfer-Encoding: 7bit" + CRLF +
                CRLF +
                "<html><body>HTML body of the message</body></html>" +
                CRLF +
                "----boundary--" + CRLF +
                "epilogue";
        MessageServerData messageServerData = new MessageServerDataBuilder()
                .messageSource(messageSource)
                .addFlag(Flag.FLAGGED)
                .build();

        LocalMessage localMessage = saveAndRestoreMessage(messageServerData);

        assertEquals(messageSource, writeLocalMessageToString(localMessage));
        assertEquals(flagSet(Flag.FLAGGED), localMessage.getFlags());
    }

    private LocalMessage saveAndRestoreMessage(MessageServerData messageServerData) throws MessagingException {
        LegacyMessage legacyMessage = LegacyMessage.createFrom(messageServerData);

        saveLegacyMessageToDatabase(legacyMessage);

        return readLocalMessageFromDatabase();
    }

    private Map<String, String> saveLegacyMessageToDatabase(LegacyMessage legacyMessage) throws MessagingException {
        return testFolder.appendMessages(Collections.singletonList(legacyMessage));
    }

    protected LocalMessage readLocalMessageFromDatabase() throws MessagingException {
        LocalMessage localMessage = testFolder.getMessage(MESSAGE_SERVER_ID);

        FetchProfile fp = new FetchProfile();
        fp.add(FetchProfile.Item.ENVELOPE);
        fp.add(FetchProfile.Item.BODY);
        testFolder.fetch(Collections.singletonList(localMessage), fp, null);
        testFolder.close();

        return localMessage;
    }

    private String writeLocalMessageToString(LocalMessage localMessage) throws IOException, MessagingException {
        ByteArrayOutputStream messageOutputStream = new ByteArrayOutputStream();
        try {
            localMessage.writeTo(messageOutputStream);
        } finally {
            messageOutputStream.close();
        }

        return new String(messageOutputStream.toByteArray());
    }

    private HashSet<Flag> flagSet(Flag... flags) {
        HashSet<Flag> result = new HashSet<Flag>(Arrays.asList(flags));
        result.add(Flag.X_DOWNLOADED_FULL);
        result.add(Flag.X_GOT_ALL_HEADERS);
        return result;
    }

    private Account createFakeAccount() throws MessagingException {
        Account account = mock(Account.class);
        when(account.getUuid()).thenReturn(ACCOUNT_UUID);
        String defaultProviderId = StorageManager.getInstance(context).getDefaultProviderId();
        when(account.getLocalStorageProviderId()).thenReturn(defaultProviderId);
        when(account.getInboxFolderName()).thenReturn("INBOX");
        LocalStore localStore = new LocalStore(account, context);
        when(account.getLocalStore()).thenReturn(localStore);

        return account;
    }


    private static class MessageServerDataBuilder {
        private Message message;
        private List<Flag> flags = new ArrayList<Flag>();

        public MessageServerDataBuilder messageSource(String messageSource) throws IOException {
            message = createMessage(messageSource);
            return this;
        }

        public MessageServerDataBuilder addFlag(Flag flag) {
            flags.add(flag);
            return this;
        }

        private Message createMessage(String messageSource) throws IOException {
            ByteArrayInputStream inputStream = streamFromString(messageSource);
            return MessageParser.parse(inputStream);
        }

        private ByteArrayInputStream streamFromString(String input) {
            return new ByteArrayInputStream(input.getBytes());
        }

        public MessageServerData build() {
            return new TestMessageServerData(message, flags);
        }
    }

    private static class TestMessageServerData implements MessageServerData {
        private final Message message;
        private final List<Flag> flags;


        public TestMessageServerData(Message message, List<Flag> flags) {
            this.message = message;
            this.flags = flags;
        }

        @Override
        public String folderServerId() {
            return TEST_FOLDER_NAME;
        }

        @Override
        public String serverId() {
            return MESSAGE_SERVER_ID;
        }

        @Override
        public long timeStamp() {
            return 0;
        }

        @Override
        public boolean isFlagSet(Flag flag) {
            return flags.contains(flag);
        }

        @Override
        public Message message() {
            return message;
        }

        @Override
        public String getServerIdForPart(Part part) {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public boolean isMessageTruncated() {
            return false;
        }
    }
}
