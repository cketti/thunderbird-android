package com.fsck.k9.provider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import com.fsck.k9.Account;
import com.fsck.k9.Preferences;
import com.fsck.k9.mail.store.UnavailableStorageException;
import com.fsck.k9.message.Body;
import com.fsck.k9.message.Header;
import com.fsck.k9.message.Message;
import com.fsck.k9.message.MessageContainer;
import com.fsck.k9.message.MessageFactory;
import com.fsck.k9.provider.EmailProvider;
import com.fsck.k9.provider.EmailProviderConstants.FolderColumns;
import com.fsck.k9.provider.EmailProviderConstants.MessageColumns;
import com.fsck.k9.provider.message.EmailProviderHelper;
import com.fsck.k9.provider.message.EmailProviderMetadata;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.Uri;
import android.test.AndroidTestCase;
import android.test.ProviderTestCase2;

public class EmailProviderTest extends ProviderTestCase2<EmailProvider>
{
    private static final String FOLDER_NAME_1 = "INBOX";
    private static final String FOLDER_NAME_2 = "Some other folder";
    private static final String MESSAGE_CONTENTS = "Hello, world!";

    private ContentResolver mResolver;
    private String mAccountUuid;
    private long mFolderId1;
    private long mFolderId2;
    private long mMessageId1;

    public EmailProviderTest()
    {
        super(EmailProvider.class, EmailProviderConstants.AUTHORITY);
    }

    private Context getTestContext() {
        Context context = null;
        try {
            Method m = AndroidTestCase.class.getMethod("getTestContext", new Class[0]);
            context = (Context) m.invoke(this, new Object[0]);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return context;
    }

    private void assertEquals(String message, InputStream expected, byte[] actual) throws IOException {
        try {
            for (int i = 0, end = actual.length; i < end; i++) {
                int b = expected.read();
                assertTrue("InputStream 'expected' contains less bytes than byte array 'actual' (" + message + ")", b != -1);
                assertEquals(message, b, actual[i]);
            }
            assertTrue("InputStream 'expected' contains more bytes than byte array 'actual'(" + message + ")", expected.read() == -1);
        } finally {
            expected.close();
        }
    }

    @Override
    public void setUp() {
        Context context = getContext();
        mResolver = context.getContentResolver();

        // Create account for testing
        Preferences preferences = Preferences.getPreferences(context);
        Account account = preferences.newAccount();
        account.setName("Testing");
        account.setDescription("Used for unit testing");
        account.setEmail("test@example.com");
        account.save(preferences);

        mAccountUuid = account.getUuid();

        createTestFolders();
    }

    private void createTestFolders() {
        Uri uri = EmailProviderConstants.Folder.getContentUri(mAccountUuid);
        ContentValues cv = new ContentValues();

        cv.put(EmailProviderConstants.FolderColumns.NAME, FOLDER_NAME_1);
        mFolderId1 = ContentUris.parseId(mResolver.insert(uri, cv));

        cv.put(EmailProviderConstants.FolderColumns.NAME, FOLDER_NAME_2);
        mFolderId2 = ContentUris.parseId(mResolver.insert(uri, cv));

        assertNotSame(mFolderId1 + " vs. " + mFolderId2, mFolderId1, mFolderId2);
    }

    private void createTestMessage() {
        MessageFactory factory = EmailProviderHelper.getFactory(mContext, mAccountUuid);

        EmailProviderMetadata metadata = (EmailProviderMetadata) factory.createMetadata();
        metadata.setFolderId(mFolderId1);

        Message message = factory.createMessage();

        Header header = message.getHeader();
        header.addEncoded("Content-Type", "text/plain");
        header.addEncoded("Subject", "Test");

        ByteArrayInputStream in = new ByteArrayInputStream(MESSAGE_CONTENTS.getBytes());
        Body body = factory.createBody(message, in);
        message.setBody(body);

        MessageContainer container = new MessageContainer(metadata, message);
        mMessageId1 = EmailProviderHelper.saveMessage(mContext, container);
    }

    @Override
    public void tearDown() {
        // Delete database (and associated files) of the account created in setUp()
        EmailProvider provider = (EmailProvider) mResolver.acquireContentProviderClient(
                EmailProviderConstants.BASE_URI).getLocalContentProvider();
        try {
            provider.deleteDatabase(mAccountUuid);
        } catch (UnavailableStorageException e) {
            e.printStackTrace();
        }

        // Delete account
        Preferences preferences = Preferences.getPreferences(getContext());
        Account account = preferences.getAccount(mAccountUuid);
        preferences.deleteAccount(account);
    }

    public void testInvalidUri() {
        Uri uri = Uri.parse(String.format("content://%s/invalid", EmailProviderConstants.AUTHORITY));
        boolean exception = false;
        try {
            mResolver.query(uri, null, null, null, null);
        } catch (IllegalArgumentException e) {
            exception = true;
        }

        assertTrue(exception);
    }

    public void testFolders() {
        Uri uri = EmailProviderConstants.Folder.getContentUri(mAccountUuid);
        String[] projection = new String[] {FolderColumns.NAME};
        Cursor cursor = mResolver.query(uri, projection, null, null, null);

        boolean folder1Found = false;
        boolean folder2Found = false;
        try {
            while (cursor.moveToNext()) {
                String name = cursor.getString(0);

                if (FOLDER_NAME_1.equals(name)) {
                    folder1Found = true;
                } else if (FOLDER_NAME_2.equals(name)) {
                    folder2Found = true;
                }
            }
        } finally {
            cursor.close();
        }

        assertTrue(folder1Found);
        assertTrue(folder2Found);
    }

    public void testFolderId() {
        checkFolderId(mFolderId1, FOLDER_NAME_1);
        checkFolderId(mFolderId2, FOLDER_NAME_2);
    }

    private void checkFolderId(long folderId, String expectedName) {
        Uri uri = ContentUris.withAppendedId(
                EmailProviderConstants.Folder.getContentUri(mAccountUuid), folderId);
        String[] projection = new String[] {FolderColumns.ID, FolderColumns.NAME};
        Cursor cursor = mResolver.query(uri, projection, null, null, null);

        long id = 0;
        String name = null;
        int count = 0;
        try {
            while (cursor.moveToNext()) {
                id = cursor.getLong(0);
                name = cursor.getString(1);
                count++;
            }
        } finally {
            cursor.close();
        }

        assertEquals(1, count);
        assertEquals(folderId, id);
        assertEquals(expectedName, name);
    }

    public void testMessages() {
        createTestMessage();

        Uri uri = EmailProviderConstants.Message.getContentUri(mAccountUuid);
        String[] projection = new String[] {
                MessageColumns.ID,
                MessageColumns.FOLDER_ID,
                };
        Cursor cursor = mResolver.query(uri, projection, null, null, null);

        int count = 0;
        boolean found = false;
        try {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                long folderId = cursor.getLong(1);

                count++;

                if (id == mMessageId1) {
                    found = true;
                    assertEquals(mFolderId1, folderId);
                }
            }
        } finally {
            cursor.close();
        }

        assertEquals(1, count);
        assertTrue(found);
    }

    public void testMessageId() {
        createTestMessage();

        Uri uri = ContentUris.withAppendedId(
                EmailProviderConstants.Message.getContentUri(mAccountUuid), mMessageId1);

        String[] projection = new String[] {MessageColumns.ID};
        Cursor cursor = mResolver.query(uri, projection, null, null, null);

        long id = 0;
        int count = 0;
        try {
            while (cursor.moveToNext()) {
                id = cursor.getLong(0);
                count++;
            }
        } finally {
            cursor.close();
        }

        assertEquals(1, count);
        assertEquals(mMessageId1, id);
    }

    public void testMessageFromFile() throws IOException {
        MessageFactory factory = EmailProviderHelper.getFactory(mContext, mAccountUuid);
        AssetManager assetManager = getTestContext().getAssets();

        String[] testFiles = new String[] {
            "simple_text_message.eml",
            "multipart_alternative.eml",
            "simple_message_rfc822.eml"
        };

        for (String filename : testFiles) {
            EmailProviderMetadata metadata = (EmailProviderMetadata) factory.createMetadata();
            metadata.setFolderId(mFolderId1);

            InputStream in = assetManager.open(filename);
            Message message = factory.createMessage(in);

            /*
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            message.writeTo(buf);
            buf.close();

            System.out.println("------");
            System.out.println(buf.toString());
            System.out.println("------");
            //*/

            MessageContainer container = new MessageContainer(metadata, message);
            long messageId = EmailProviderHelper.saveMessage(mContext, container);

            MessageContainer container2 = EmailProviderHelper.restoreMessageWithId(mContext, mAccountUuid, messageId);

            InputStream asset = assetManager.open(filename);

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            container2.getMessage().writeTo(buffer);
            buffer.close();

            /*
            System.out.println("======");
            System.out.println(buffer.toString());
            System.out.println("======");
            //*/

            assertEquals(filename, asset, buffer.toByteArray());
        }
    }
}
