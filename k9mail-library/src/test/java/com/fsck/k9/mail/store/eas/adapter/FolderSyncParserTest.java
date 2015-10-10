package com.fsck.k9.mail.store.eas.adapter;


import java.io.ByteArrayInputStream;
import java.io.InputStream;

import com.fsck.k9.mail.store.eas.Eas;
import com.fsck.k9.mail.store.eas.adapter.Parser.EasParserException;
import com.fsck.k9.mail.store.eas.callback.FolderSyncCallback;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.MockitoAnnotations.initMocks;


@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class FolderSyncParserTest {
    @Mock
    private FolderSyncCallback callback;
    @Mock
    private FolderSyncController controller;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
    }

    @Test
    public void parseSimpleFolderSyncResponse() throws Exception {
        Serializer serializer = new Serializer();
        serializer.start(Tags.FOLDER_FOLDER_SYNC)
                .start(Tags.FOLDER_STATUS).text("1").end()
                .start(Tags.FOLDER_SYNC_KEY).text("1").end()
                .start(Tags.FOLDER_CHANGES)
                .start(Tags.FOLDER_COUNT).text("0").end()
                .end()
                .end().done();
        InputStream inputStream = inputStreamFromSerializer(serializer);
        FolderSyncParser parser = new FolderSyncParser(inputStream, callback, controller);

        parser.parse();

        verify(controller).folderStatus(1);
        verify(controller).updateSyncKey("1");
        verify(callback, never()).clearFolders();
        verify(callback, never()).addFolder(anyString(), anyString(), anyInt(), anyString());
        verify(callback, never()).changeFolder(anyString(), anyString(), anyString());
        verify(callback, never()).removeFolder(anyString());
        verify(callback).commitFolderChanges();
    }

    @Test
    public void parseFolderSyncResponseWithAddedFolders() throws Exception {
        Serializer serializer = new Serializer();
        serializer.start(Tags.FOLDER_FOLDER_SYNC)
                /**/.start(Tags.FOLDER_STATUS).text("1").end()
                /**/.start(Tags.FOLDER_SYNC_KEY).text("42").end()
                /**/.start(Tags.FOLDER_CHANGES)
                /*....*/.start(Tags.FOLDER_COUNT).text("2").end()
                /*....*/.start(Tags.FOLDER_ADD)
                /*........*/.start(Tags.FOLDER_SERVER_ID).text("Folder1").end()
                /*........*/.start(Tags.FOLDER_PARENT_ID).text("0").end()
                /*........*/.start(Tags.FOLDER_DISPLAY_NAME).text("Folder #1").end()
                /*........*/.start(Tags.FOLDER_TYPE).text(Integer.toString(Eas.MAILBOX_TYPE_INBOX)).end()
                /*....*/.end()
                /*....*/.start(Tags.FOLDER_ADD)
                /*........*/.start(Tags.FOLDER_SERVER_ID).text("Folder2").end()
                /*........*/.start(Tags.FOLDER_PARENT_ID).text("Folder1").end()
                /*........*/.start(Tags.FOLDER_DISPLAY_NAME).text("Folder #2").end()
                /*........*/.start(Tags.FOLDER_TYPE).text(Integer.toString(Eas.MAILBOX_TYPE_USER_MAIL)).end()
                /*....*/.end()
                /**/.end()
                .end().done();
        InputStream inputStream = inputStreamFromSerializer(serializer);
        FolderSyncParser parser = new FolderSyncParser(inputStream, callback, controller);

        parser.parse();

        verify(callback).addFolder("Folder1", "Folder #1", Eas.MAILBOX_TYPE_INBOX, "0");
        verify(callback).addFolder("Folder2", "Folder #2", Eas.MAILBOX_TYPE_USER_MAIL, "Folder1");
        verify(callback).commitFolderChanges();
    }

    @Test
    public void parseFolderSyncResponseWithDeletedFolder() throws Exception {
        Serializer serializer = new Serializer();
        serializer.start(Tags.FOLDER_FOLDER_SYNC)
                /**/.start(Tags.FOLDER_STATUS).text("1").end()
                /**/.start(Tags.FOLDER_SYNC_KEY).text("42").end()
                /**/.start(Tags.FOLDER_CHANGES)
                /*....*/.start(Tags.FOLDER_DELETE)
                /*........*/.start(Tags.FOLDER_SERVER_ID).text("FolderServerId1").end()
                /*....*/.end()
                /**/.end()
                .end().done();
        InputStream inputStream = inputStreamFromSerializer(serializer);
        FolderSyncParser parser = new FolderSyncParser(inputStream, callback, controller);

        parser.parse();

        verify(callback).removeFolder("FolderServerId1");
        verify(callback).commitFolderChanges();
    }

    @Test
    public void parseFolderSyncResponseWithChangedFolder() throws Exception {
        Serializer serializer = new Serializer();
        serializer.start(Tags.FOLDER_FOLDER_SYNC)
                /**/.start(Tags.FOLDER_STATUS).text("1").end()
                /**/.start(Tags.FOLDER_SYNC_KEY).text("abc").end()
                /**/.start(Tags.FOLDER_CHANGES)
                /*....*/.start(Tags.FOLDER_UPDATE)
                /*........*/.start(Tags.FOLDER_SERVER_ID).text("Folder1").end()
                /*........*/.start(Tags.FOLDER_PARENT_ID).text("0").end()
                /*........*/.start(Tags.FOLDER_DISPLAY_NAME).text("Folder #2").end()
                /*........*/.start(Tags.FOLDER_TYPE).text(Integer.toString(Eas.MAILBOX_TYPE_USER_MAIL)).end()
                /*....*/.end()
                /**/.end()
                .end().done();
        InputStream inputStream = inputStreamFromSerializer(serializer);
        FolderSyncParser parser = new FolderSyncParser(inputStream, callback, controller);

        parser.parse();

        verify(callback).changeFolder("Folder1", "Folder #2", "0");
        verify(callback).commitFolderChanges();
    }

    @Test
    public void parseFolderSyncResponseWithDeletedFolderAndUnknownTags() throws Exception {
        Serializer serializer = new Serializer();
        serializer.start(Tags.FOLDER_FOLDER_SYNC)
                /**/.start(Tags.SYNC_SYNC).text("0").end()
                /**/.start(Tags.FOLDER_STATUS).text("1").end()
                /**/.start(Tags.SYNC_STATUS).text("9").end()
                /**/.start(Tags.FOLDER_SYNC_KEY).text("23").end()
                /**/.start(Tags.FOLDER_CHANGES)
                /*....*/.start(Tags.SYNC_ADD).end()
                /*....*/.start(Tags.FOLDER_DELETE)
                /*........*/.start(Tags.FOLDER_SERVER_ID).text("FolderServerId1").end()
                /*........*/.start(Tags.SYNC_SERVER_ID).text("something").end()
                /*....*/.end()
                /*....*/.start(Tags.FOLDER_ADD)
                /*........*/.start(Tags.FOLDER_SERVER_ID).text("Folder1").end()
                /*........*/.start(Tags.FOLDER_PARENT_ID).text("0").end()
                /*........*/.start(Tags.FOLDER_DISPLAY_NAME).text("Folder #1").end()
                /*........*/.start(Tags.RECIPIENTS_TYPE).text("unknown").end()
                /*........*/.start(Tags.FOLDER_TYPE).text(Integer.toString(Eas.MAILBOX_TYPE_INBOX)).end()
                /*....*/.end()
                /*....*/.start(Tags.FOLDER_UPDATE)
                /*........*/.start(Tags.FOLDER_SERVER_ID).text("Folder1").end()
                /*........*/.start(Tags.SYNC_APPLICATION_DATA).end()
                /*........*/.start(Tags.FOLDER_PARENT_ID).text("0").end()
                /*........*/.start(Tags.FOLDER_DISPLAY_NAME).text("Folder #2").end()
                /*........*/.start(Tags.FOLDER_TYPE).text(Integer.toString(Eas.MAILBOX_TYPE_USER_MAIL)).end()
                /*....*/.end()
                /**/.end()
                .end().done();
        InputStream inputStream = inputStreamFromSerializer(serializer);
        FolderSyncParser parser = new FolderSyncParser(inputStream, callback, controller);

        parser.parse();

        verify(controller).updateSyncKey("23");
        verify(controller).folderStatus(1);
        verify(callback).removeFolder("FolderServerId1");
        verify(callback).addFolder("Folder1", "Folder #1", Eas.MAILBOX_TYPE_INBOX, "0");
        verify(callback).changeFolder("Folder1", "Folder #2", "0");
        verify(callback).commitFolderChanges();
        verifyNoMoreInteractions(controller, callback);
    }

    @Test(expected = EasParserException.class)
    public void parseNonFolderSyncResponse() throws Exception {
        Serializer serializer = new Serializer();
        serializer.start(Tags.SYNC_SYNC).end().done();
        InputStream inputStream = inputStreamFromSerializer(serializer);
        FolderSyncParser parser = new FolderSyncParser(inputStream, callback, controller);

        parser.parse();
    }

    @Test
    public void createFolderSyncParserWithNullCallback() throws Exception {
        Serializer serializer = new Serializer();
        serializer.start(Tags.FOLDER_FOLDER_SYNC).end().done();
        InputStream inputStream = inputStreamFromSerializer(serializer);

        try {
            new FolderSyncParser(inputStream, null, controller);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Argument 'callback' can't be null", e.getMessage());
        }
    }

    @Test
    public void createFolderSyncParserWithNullController() throws Exception {
        Serializer serializer = new Serializer();
        serializer.start(Tags.FOLDER_FOLDER_SYNC).end().done();
        InputStream inputStream = inputStreamFromSerializer(serializer);

        try {
            new FolderSyncParser(inputStream, callback, null);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Argument 'controller' can't be null", e.getMessage());
        }
    }

    private ByteArrayInputStream inputStreamFromSerializer(Serializer serializer) {
        return new ByteArrayInputStream(serializer.toByteArray());
    }
}
