package com.fsck.k9.controller;

import com.fsck.k9.Account;
import com.fsck.k9.Preferences;
import com.fsck.k9.Account.FolderMode;
import com.fsck.k9.mail.store.UnavailableStorageException;
import com.fsck.k9.provider.EmailProvider;
import com.fsck.k9.provider.EmailProviderConstants;
import com.fsck.k9.provider.message.EmailProviderFolder;
import com.fsck.k9.K9;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
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
        account.save(preferences);

        mAccount = account;
    }

    public void testListFolders() {
        Context context = getContext();

        // Create test folder
        createTestFolder(context, mAccount.getUuid(), "Test1");

        K9.DEBUG = true;
        MessagingController controller = MessagingController.getInstance(context);

        ListFoldersMessageListener listener = new ListFoldersMessageListener();
        controller.listFoldersSynchronous(mAccount, false, listener);

        controller.stop();

        assertTrue(listener.listFoldersStarted);
        assertTrue(listener.listFolders);
        assertTrue(listener.listFoldersFinished);
    }

    private long createTestFolder(Context context, String accountUuid, String folderName) {
        Uri uri = EmailProviderConstants.Folder.getContentUri(accountUuid);
        ContentValues cv = new ContentValues();

        cv.put(EmailProviderConstants.FolderColumns.NAME, folderName);
        return ContentUris.parseId(context.getContentResolver().insert(uri, cv));
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
}
