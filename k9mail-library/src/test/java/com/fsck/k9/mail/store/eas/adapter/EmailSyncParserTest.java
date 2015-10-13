package com.fsck.k9.mail.store.eas.adapter;


import java.io.InputStream;

import com.fsck.k9.mail.Address;
import com.fsck.k9.mail.store.eas.Eas;
import com.fsck.k9.mail.store.eas.Mailbox;
import com.fsck.k9.mail.store.eas.callback.EmailSyncCallback;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static com.fsck.k9.mail.store.eas.adapter.ParserTestHelper.inputStreamFromSerializer;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;


@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class EmailSyncParserTest {
    private static final String CRLF = "\r\n";
    private static final String SIMPLE_MESSAGE = "" +
            "From: alice@example.org" + CRLF +
            "To: bob@example.org" + CRLF +
            "Date: Mon, 12 Oct 2015 12:34:56 +0200" + CRLF +
            "Mime-Version: 1.0" + CRLF +
            "Content-Type: text/plain; charset=UTF-8" + CRLF +
            "Subject: Test" + CRLF +
            CRLF +
            "This is a test" + CRLF;


    @Mock
    private EmailSyncCallback callback;


    @Before
    public void setUp() throws Exception {
        initMocks(this);
    }

    @Test
    public void parseEas2_5ResponseContainingSingleSimpleMessage() throws Exception {
        Serializer serializer = new Serializer();
        serializer.start(Tags.SYNC_SYNC)
                /**/.start(Tags.SYNC_COLLECTIONS)
                /*....*/.start(Tags.SYNC_COLLECTION)
                /*........*/.start(Tags.SYNC_CLASS).text(Eas.getFolderClass(Mailbox.TYPE_MAIL)).end()
                /*........*/.start(Tags.SYNC_SYNC_KEY).text("23").end()
                /*........*/.start(Tags.SYNC_COLLECTION_ID).text("INBOX").end()
                /*........*/.start(Tags.SYNC_STATUS).text("1").end()
                /*........*/.start(Tags.SYNC_COMMANDS)
                /*............*/.start(Tags.SYNC_ADD)
                /*................*/.start(Tags.SYNC_SERVER_ID).text("Msg1").end()
                /*................*/.start(Tags.SYNC_APPLICATION_DATA)
                /*....................*/.start(Tags.EMAIL_TO).text("bob@example.org").end()
                /*....................*/.start(Tags.EMAIL_FROM).text("alice@example.org").end()
                /*....................*/.start(Tags.EMAIL_REPLY_TO).text("alice@example.org").end()
                /*....................*/.start(Tags.EMAIL_SUBJECT).text("Test").end()
                /*....................*/.start(Tags.EMAIL_DATE_RECEIVED).text("2015-10-12T20:00:00.000Z").end()
                /*....................*/.start(Tags.EMAIL_DISPLAY_TO).text("alice@example.org").end()
                /*....................*/.start(Tags.EMAIL_IMPORTANCE).text("1").end()
                /*....................*/.start(Tags.EMAIL_READ).text("1").end()
                /*....................*/.start(Tags.EMAIL_MESSAGE_CLASS).text("IPM.Note").end()
                /*....................*/.start(Tags.EMAIL_MIME_DATA).text(SIMPLE_MESSAGE).end()
                /*................*/.end()
                /*............*/.end()
                /*........*/.end()
                /*....*/.end()
                /**/.end()
                .end().done();
        InputStream inputStream = inputStreamFromSerializer(serializer);
        EmailSyncParser parser = new EmailSyncParser(inputStream, callback);

        parser.parse();

        verifyParserResultForSimpleMessageResponse();
    }

    @Test
    public void parseEas12_0ResponseContainingSingleSimpleMessage() throws Exception {
        Serializer serializer = new Serializer();
        serializer.start(Tags.SYNC_SYNC)
                /**/.start(Tags.SYNC_COLLECTIONS)
                /*....*/.start(Tags.SYNC_COLLECTION)
                /*........*/.start(Tags.SYNC_CLASS).text(Eas.getFolderClass(Mailbox.TYPE_MAIL)).end()
                /*........*/.start(Tags.SYNC_SYNC_KEY).text("23").end()
                /*........*/.start(Tags.SYNC_COLLECTION_ID).text("INBOX").end()
                /*........*/.start(Tags.SYNC_STATUS).text("1").end()
                /*........*/.start(Tags.SYNC_COMMANDS)
                /*............*/.start(Tags.SYNC_ADD)
                /*................*/.start(Tags.SYNC_SERVER_ID).text("Msg1").end()
                /*................*/.start(Tags.SYNC_APPLICATION_DATA)
                /*....................*/.start(Tags.BASE_BODY)
                /*........................*/.start(Tags.BASE_TYPE).text(Eas.BODY_PREFERENCE_MIME).end()
                /*........................*/.start(Tags.BASE_DATA).text(SIMPLE_MESSAGE).end()
                /*....................*/.end()
                /*....................*/.start(Tags.EMAIL_TO).text("bob@example.org").end()
                /*....................*/.start(Tags.EMAIL_FROM).text("alice@example.org").end()
                /*....................*/.start(Tags.EMAIL_REPLY_TO).text("alice@example.org").end()
                /*....................*/.start(Tags.EMAIL_SUBJECT).text("Test").end()
                /*....................*/.start(Tags.EMAIL_DATE_RECEIVED).text("2015-10-12T20:00:00.000Z").end()
                /*....................*/.start(Tags.EMAIL_DISPLAY_TO).text("alice@example.org").end()
                /*....................*/.start(Tags.EMAIL_IMPORTANCE).text("1").end()
                /*....................*/.start(Tags.EMAIL_READ).text("1").end()
                /*....................*/.start(Tags.EMAIL_MESSAGE_CLASS).text("IPM.Note").end()
                /*................*/.end()
                /*............*/.end()
                /*........*/.end()
                /*....*/.end()
                /**/.end()
                .end().done();
        InputStream inputStream = inputStreamFromSerializer(serializer);
        EmailSyncParser parser = new EmailSyncParser(inputStream, callback);

        parser.parse();

        verifyParserResultForSimpleMessageResponse();
    }

    private void verifyParserResultForSimpleMessageResponse() {
        ArgumentCaptor<MessageData> argumentCaptor = ArgumentCaptor.forClass(MessageData.class);
        verify(callback).addMessage(argumentCaptor.capture());
        MessageData messageData = argumentCaptor.getValue();
        assertEquals("Msg1", messageData.getServerId());
        assertArrayEquals(Address.parse("bob@example.org"), messageData.getTo());
        assertArrayEquals(Address.parse("alice@example.org"), messageData.getFrom());
        assertArrayEquals(Address.parse("alice@example.org"), messageData.getReplyTo());
        assertEquals("Test", messageData.getSubject());
        assertEquals(1444680000000L, messageData.getTimeStamp());
        assertEquals(true, messageData.isFlagRead());
        assertEquals(SIMPLE_MESSAGE, messageData.getMessageData());
        verify(callback).commitMessageChanges();
    }
}
