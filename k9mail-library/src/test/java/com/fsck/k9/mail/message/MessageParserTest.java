package com.fsck.k9.mail.message;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import com.fsck.k9.mail.data.Body;
import com.fsck.k9.mail.data.ContentBody;
import com.fsck.k9.mail.data.Message;
import com.fsck.k9.mail.data.Multipart;
import com.fsck.k9.mail.data.Part;
import okio.BufferedSource;
import okio.Okio;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class MessageParserTest {
    private static final String CRLF = "\r\n";
    private static final Charset DEFAULT_CHARSET = Charset.forName("us-ascii");


    @Test
    public void parseSimplePlainTextMessage() throws Exception {
        String messageSource = "" +
                "From: alice@example.org" + CRLF +
                "To: bob@example.org" + CRLF +
                "Subject: Test" + CRLF +
                "Date: Wed, 21 Oct 2015 00:42:23 +0200" + CRLF +
                "Content-Type: text/plain" + CRLF +
                "Mime-Version: 1.0" + CRLF +
                CRLF +
                "This is a test";
        ByteArrayInputStream inputStream = streamFromString(messageSource);

        Message message = MessageParser.parse(inputStream);

        assertNotNull(message);
        assertNotNull(message.header());
        assertEquals("alice@example.org", message.header().value("From"));
        assertEquals("bob@example.org", message.header().value("To"));
        assertEquals("Test", message.header().value("Subject"));
        assertEquals("Wed, 21 Oct 2015 00:42:23 +0200", message.header().value("Date"));
        assertEquals("text/plain", message.header().value("Content-Type"));
        assertEquals("1.0", message.header().value("Mime-Version"));
        assertNotNull(message.body());
        assertEquals("This is a test", bodyToString(message.body()));
    }

    @Test
    public void parseMultipartAlternativeMessage() throws Exception {
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
        ByteArrayInputStream inputStream = streamFromString(messageSource);

        Message message = MessageParser.parse(inputStream);

        assertNotNull(message);
        assertNotNull(message.header());
        assertEquals("multipart/alternative; boundary=--boundary", message.header().value("Content-Type"));
        assertNotNull(message.body());
        assertTrue(message.body() instanceof Multipart);
        Multipart multipart = (Multipart) message.body();
        assertNotNull(multipart.preamble());
        assertEquals("This is a message with multiple parts in MIME format.", byteArrayToString(multipart.preamble()));
        assertNotNull(multipart.epilogue());
        assertEquals("epilogue", byteArrayToString(multipart.epilogue()));
        assertEquals(2, multipart.size());
        assertEquals(multipart.size(), multipart.children().size());
        Part textPlainPart = multipart.children().get(0);
        assertNotNull(textPlainPart);
        assertNotNull(textPlainPart.header());
        assertEquals("text/plain", textPlainPart.header().value("Content-Type"));
        assertNotNull(textPlainPart.body());
        assertEquals("This is a plain text body.", bodyToString(textPlainPart.body()));
        Part textHtmlPart = multipart.children().get(1);
        assertNotNull(textHtmlPart);
        assertNotNull(textHtmlPart.header());
        assertEquals("text/html", textHtmlPart.header().value("Content-Type"));
        assertNotNull(textHtmlPart.body());
        assertEquals("<html><body>HTML body of the message</body></html>", bodyToString(textHtmlPart.body()));
    }

    @Test
    public void parseMessageContainingInnerMessage() throws Exception {
        String messageSource = "" +
                "From: charles@example.org" + CRLF +
                "To: dora@example.org" + CRLF +
                "Subject: Inner message" + CRLF +
                "Date: Wed, 21 Oct 2015 12:34:56 +0200" + CRLF +
                "Content-Type: message/rfc822" + CRLF +
                "Mime-Version: 1.0" + CRLF +
                CRLF +
                "From: alice@example.org" + CRLF +
                "To: bob@example.org" + CRLF +
                "Subject: Test" + CRLF +
                "Date: Wed, 21 Oct 2015 00:42:23 +0200" + CRLF +
                "Content-Type: text/plain" + CRLF +
                "Mime-Version: 1.0" + CRLF +
                CRLF +
                "This is a test of an inner message";
        ByteArrayInputStream inputStream = streamFromString(messageSource);

        Message message = MessageParser.parse(inputStream);

        assertNotNull(message);
        assertNotNull(message.header());
        assertNotNull(message.body());
        assertEquals("charles@example.org", message.header().value("From"));
        assertEquals("dora@example.org", message.header().value("To"));
        assertEquals("Inner message", message.header().value("Subject"));
        assertEquals("Wed, 21 Oct 2015 12:34:56 +0200", message.header().value("Date"));
        assertEquals("message/rfc822", message.header().value("Content-Type"));
        assertEquals("1.0", message.header().value("Mime-Version"));
        assertTrue(message.body() instanceof Message);
        Message innerMessage = (Message) message.body();
        assertNotNull(innerMessage);
        assertNotNull(innerMessage.header());
        assertEquals("alice@example.org", innerMessage.header().value("From"));
        assertEquals("bob@example.org", innerMessage.header().value("To"));
        assertEquals("Test", innerMessage.header().value("Subject"));
        assertEquals("Wed, 21 Oct 2015 00:42:23 +0200", innerMessage.header().value("Date"));
        assertEquals("text/plain", innerMessage.header().value("Content-Type"));
        assertEquals("1.0", innerMessage.header().value("Mime-Version"));
        assertNotNull(innerMessage.body());
        assertEquals("This is a test of an inner message", bodyToString(innerMessage.body()));
    }

    private ByteArrayInputStream streamFromString(String input) {
        return new ByteArrayInputStream(input.getBytes());
    }

    private String bodyToString(Body body) throws IOException {
        assertTrue(body instanceof ContentBody);
        ContentBody contentBody = (ContentBody) body;
        InputStream raw = contentBody.raw();
        try {
            BufferedSource bufferedSource = Okio.buffer(Okio.source(raw));
            return bufferedSource.readString(DEFAULT_CHARSET);
        } finally {
            raw.close();
        }
    }

    private String byteArrayToString(byte[] input) {
        return new String(input, DEFAULT_CHARSET);
    }
}
