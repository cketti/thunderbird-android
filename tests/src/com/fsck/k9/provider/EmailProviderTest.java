package com.fsck.k9.provider;

import com.fsck.k9.Account;
import com.fsck.k9.Preferences;
import com.fsck.k9.provider.EmailProvider;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.test.ProviderTestCase2;

public class EmailProviderTest extends ProviderTestCase2<EmailProvider>
{
    private String mAccountUuid;
    private ContentResolver mResolver;

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

        // Create some folders
        Uri uri = EmailProviderConstants.Folder.getContentUri(mAccountUuid);
        ContentValues cv = new ContentValues();

        cv.put(EmailProviderConstants.FolderColumns.NAME, "INBOX");
        mResolver.insert(uri, cv);

        cv.put(EmailProviderConstants.FolderColumns.NAME, "Folder1");
        mResolver.insert(uri, cv);
    }

    @Override
    public void tearDown() {
        // Delete account created in setUp()
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
        String[] projection = new String[] {"name"};
        String selection = null;
        String[] selectionArgs = null;
        String sortOrder = null;
        Cursor cursor = mResolver.query(uri, projection, selection, selectionArgs, sortOrder);

        boolean found = false;
        while (cursor.moveToNext()) {
            String name = cursor.getString(0);

            if ("INBOX".equals(name)) {
                found = true;
            }
        }

        cursor.close();

        assertTrue(found);
    }

    public void testFolderId() {
        long folderId = 1;
        Uri uri = ContentUris.withAppendedId(
                EmailProviderConstants.Folder.getContentUri(mAccountUuid), folderId);
        String[] projection = new String[] {"id", "name"};
        String selection = null;
        String[] selectionArgs = null;
        String sortOrder = null;
        Cursor cursor = mResolver.query(uri, projection, selection, selectionArgs, sortOrder);

        long id = 0;
        int count = 0;
        while (cursor.moveToNext()) {
            id = cursor.getLong(0);
            //String name = cursor.getString(1);
            count++;
        }

        cursor.close();

        assertEquals(1, count);
        assertEquals(folderId, id);
    }

}
