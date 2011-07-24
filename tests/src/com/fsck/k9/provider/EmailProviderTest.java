package com.fsck.k9.provider;

import com.fsck.k9.Account;
import com.fsck.k9.Preferences;
import com.fsck.k9.mail.store.UnavailableStorageException;
import com.fsck.k9.provider.EmailProvider;
import com.fsck.k9.provider.EmailProviderConstants.FolderColumns;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.test.ProviderTestCase2;

public class EmailProviderTest extends ProviderTestCase2<EmailProvider>
{
    private static final String FOLDER_NAME_1 = "INBOX";
    private static final String FOLDER_NAME_2 = "Some other folder";

    private ContentResolver mResolver;
    private String mAccountUuid;
    private long mFolderId1;
    private long mFolderId2;

    public EmailProviderTest()
    {
        super(EmailProvider.class, EmailProviderConstants.AUTHORITY);
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
}
