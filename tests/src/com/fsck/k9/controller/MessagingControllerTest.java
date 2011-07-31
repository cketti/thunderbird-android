package com.fsck.k9.controller;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Properties;
import com.fsck.k9.Account;
import com.fsck.k9.K9;
import com.fsck.k9.Preferences;
import com.fsck.k9.TestHelper;
import com.fsck.k9.Account.FolderMode;
import com.fsck.k9.mail.store.UnavailableStorageException;
import com.fsck.k9.message.Body;
import com.fsck.k9.message.Header;
import com.fsck.k9.message.Message;
import com.fsck.k9.message.MessageContainer;
import com.fsck.k9.provider.EmailProvider;
import com.fsck.k9.provider.EmailProviderConstants;
import com.fsck.k9.provider.message.EmailProviderFolder;
import com.fsck.k9.provider.message.EmailProviderHelper;
import com.fsck.k9.provider.message.EmailProviderMessageFactory;
import com.fsck.k9.provider.message.EmailProviderMetadata;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import android.test.AndroidTestCase;

/**
 * Integration tests for {@link MessagingController}.
 */
public class MessagingControllerTest extends AndroidTestCase {
    private Account mAccount;

    @Override
    public void setUp() {
        Context context = getContext();

        // Delete all accounts
        Preferences preferences = Preferences.getPreferences(context);
        Account[] accounts = preferences.getAccounts();
        for (Account account : accounts) {
            EmailProvider provider = (EmailProvider) context.getContentResolver()
                    .acquireContentProviderClient(EmailProviderConstants.BASE_URI)
                    .getLocalContentProvider();
            try {
                provider.deleteDatabase(account.getUuid());
            } catch (UnavailableStorageException e) {
                e.printStackTrace();
            }
            preferences.deleteAccount(account);
        }

        // Create new account
        Account account = preferences.newAccount();
        account.setName("Testing");
        account.setDescription("Used for testing");
        account.setEmail("test@example.com");
        account.setAutomaticCheckIntervalMinutes(-1);
        account.setFolderPushMode(FolderMode.NONE);

        // Read outgoing server settings from properties file
        AssetManager assetManager = TestHelper.getTestContext(this).getAssets();
        try {
            Properties properties = new Properties();
            properties.load(assetManager.open("server_settings.properties"));

            String serverType = properties.getProperty("outgoing.serverType");
            String username = properties.getProperty("outgoing.username");
            String password = properties.getProperty("outgoing.password");
            String host = properties.getProperty("outgoing.host");
            int port = Integer.parseInt(properties.getProperty("outgoing.port"));

            String usernameEnc = URLEncoder.encode(username, "UTF-8");
            String passwordEnc = URLEncoder.encode(password, "UTF-8");
            String storeUri = new URI(
                    serverType,
                    usernameEnc + ":" + passwordEnc,
                    host,
                    port,
                    null,
                    null,
                    null).toString();
            account.setStoreUri(storeUri);
        }
        catch (Exception e) {
            fail("Failed to get the outgoing server settings: " + e.getMessage());
        }

        account.save(preferences);

        mAccount = account;
    }

    public void xtestListFolders() {
        Context context = getContext();

        // Create test folder
        createTestFolder(context, mAccount.getUuid(), "Test1");

        MessagingController controller = MessagingController.getInstance(context);

        ListFoldersMessageListener listener = new ListFoldersMessageListener();
        controller.listFoldersSynchronous(mAccount, false, listener);

        controller.stop();

        assertTrue(listener.listFoldersStarted);
        assertTrue(listener.listFolders);
        assertTrue(listener.listFoldersFinished);
    }

    public void testCheckMail() {
        Context context = getContext();
        String folderName = "INBOX";

        long folderId = createTestFolder(context, mAccount.getUuid(), folderName);

        createTestMessage(mContext, mAccount.getUuid(), folderId, "MessageText1");

        K9.DEBUG = true;
        MessagingController controller = MessagingController.getInstance(context);
        CheckMailListener listener = new CheckMailListener();

        controller.synchronizeMailbox(mAccount, folderName, listener, null);

        controller.stop();

        assertNull(listener.synchronizeMailboxFailed, listener.synchronizeMailboxFailed);
    }


    private long createTestFolder(Context context, String accountUuid, String folderName) {
        Uri uri = EmailProviderConstants.Folder.getContentUri(accountUuid);
        ContentValues cv = new ContentValues();

        cv.put(EmailProviderConstants.FolderColumns.NAME, folderName);
        return ContentUris.parseId(context.getContentResolver().insert(uri, cv));
    }

    private long createTestMessage(Context context, String accountUuid, long folderId,
            String messageText) {

        EmailProviderMessageFactory factory = EmailProviderHelper.getFactory(context, accountUuid);
        EmailProviderMetadata metadata = factory.createMetadata();
        metadata.setFolderId(folderId);
        metadata.setServerId("1");
        Message message = factory.createMessage();

        Header header = message.getHeader();
        header.addEncoded("Content-Type", "text/plain");
        header.addEncoded("Subject", messageText);
        header.addEncoded("To", "test@example.com");
        header.addEncoded("From", "test@example.com");

        ByteArrayInputStream in = new ByteArrayInputStream(messageText.getBytes());
        Body body = factory.createBody(message, in);
        message.setBody(body);

        MessageContainer container = new MessageContainer(metadata, message);

        return EmailProviderHelper.saveMessage(context, container);
    }

    private static class ListFoldersMessageListener extends MessagingListener {
        public volatile boolean listFoldersStarted = false;
        public volatile boolean listFoldersFinished = false;
        public volatile boolean listFolders = false;

        @Override
        public void listFoldersStarted(Account account) {
            listFoldersStarted = true;
        }

        @Override
        public void listFoldersFinished(Account account) {
            listFoldersFinished = true;
        }

        @Override
        public void listFolders(Account account, EmailProviderFolder[] folders) {
            listFolders = true;

            assertNotNull(folders);
        }

        @Override
        public void listFoldersFailed(Account account, String message) {
            fail("listFoldersFailed: " + message);
        }
    }

    private static class CheckMailListener extends MessagingListener {
        public String synchronizeMailboxFailed = null;

        @Override
        public void synchronizeMailboxFailed(Account account, String folder, String message) {
            synchronizeMailboxFailed = message;
        }
    }
}
