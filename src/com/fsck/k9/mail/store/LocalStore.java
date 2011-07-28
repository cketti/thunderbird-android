
package com.fsck.k9.mail.store;

import android.app.Application;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import com.fsck.k9.Account;
import com.fsck.k9.AccountStats;
import com.fsck.k9.controller.MessageRemovalListener;
import com.fsck.k9.controller.MessageRetrievalListener;
import com.fsck.k9.mail.Body;
import com.fsck.k9.mail.BodyPart;
import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.Folder;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Store;

public class LocalStore extends Store {

    public LocalStore(Account account) {
        super(account);
    }

    @Override
    public void checkSettings() throws MessagingException {
        throw new RuntimeException("Stub");
    }

    @Override
    public LocalFolder getFolder(String name) {
        throw new RuntimeException("Stub");
    }

    @Override
    public List<? extends Folder> getPersonalNamespaces(boolean forceListAll)
            throws MessagingException {
        throw new RuntimeException("Stub");
    }

    public void resetVisibleLimits(int limit) {
        throw new RuntimeException("Stub");
    }

    public long getSize() {
        throw new RuntimeException("Stub");
    }

    public void getMessageCounts(AccountStats stats) {
        throw new RuntimeException("Stub");
    }

    public void switchLocalStorage(String provider) {
        throw new RuntimeException("Stub");
    }

    public void createFolders(List<LocalFolder> folders, int displayCount) {
        throw new RuntimeException("Stub");
    }

    public void searchForMessages(MessageRetrievalListener retrievalListener, String[] queryFields,
                                  String query, List<LocalFolder> foldersToSearch,
                                  Message[] messages, Flag[] requiredFlags, Flag[] forbiddenFlags) {
        throw new RuntimeException("Stub");
    }

    public void addPendingCommand(PendingCommand command) {
        throw new RuntimeException("Stub");
    }

    public ArrayList<PendingCommand> getPendingCommands() {
        throw new RuntimeException("Stub");
    }

    public void removePendingCommand(PendingCommand command) {
        throw new RuntimeException("Stub");
    }


    public abstract class LocalMessage extends Message {

        public abstract boolean hasAttachments();
        public abstract long getId();
        public abstract String getPreview();
        public abstract String getTextForDisplay();
        public abstract boolean toMe();
        public abstract boolean ccMe();
    }


    public abstract class LocalFolder extends Folder {

        protected LocalFolder(Account account) {
            super(account);
        }

        public abstract boolean isIntegrate();
        public abstract int getVisibleLimit();
        public abstract void setVisibleLimit(int limit);
        public abstract void updateLastUid();
        public abstract void destroyMessages(Message[] array);
        public abstract void setUnreadMessageCount(int unreadMessageCount);
        public abstract void setFlaggedMessageCount(int flaggedMessageCount);
        public abstract String getPushState();
        public abstract void setPushState(String newPushState);
        public abstract void purgeToVisibleLimit(MessageRemovalListener messageRemovalListener);
        public abstract long getOldestMessageDate();
        public abstract Message storeSmallMessage(Message message, Runnable runnable) throws MessagingException;
        public abstract void changeUid(Message message);
        public abstract void clearMessagesOlderThan(long l);
        public abstract void updateMessage(LocalMessage message);
        public abstract String getId();
        public abstract Long getLastUid();
        public abstract Enum<FolderClass> getRawSyncClass();
        public abstract Enum<FolderClass> getRawPushClass();
        public abstract void setInTopGroup(boolean checked);
        public abstract void setIntegrate(boolean checked);
        public abstract void setDisplayClass(FolderClass valueOf);
        public abstract void setSyncClass(FolderClass valueOf);
        public abstract void setPushClass(FolderClass valueOf);
        public abstract void save();
        public abstract void clearAllMessages();
    }


    public static class PendingCommand {

        public String command;
        public String[] arguments;

    }


    public class AttachmentInfo {

        public String type;
        public String name;
        public long size;

    }


    public abstract class LocalAttachmentBodyPart extends BodyPart {

        public abstract long getAttachmentId();
    }


    public static class LocalAttachmentBody implements Body {

        public LocalAttachmentBody(Uri uri, Application application) {
            throw new RuntimeException("Stub");
        }

        @Override
        public InputStream getInputStream() throws MessagingException {
            throw new RuntimeException("Stub");
        }

        @Override
        public void writeTo(OutputStream out) throws IOException, MessagingException {
            throw new RuntimeException("Stub");
        }

        public Uri getContentUri() {
            throw new RuntimeException("Stub");
        }

    }

    public AttachmentInfo getAttachmentInfo(String id) {
        throw new RuntimeException("Stub");
    }

    public void removePendingCommands() {
        throw new RuntimeException("Stub");
    }

    public void compact() {
        throw new RuntimeException("Stub");
    }

    public void clear() {
        throw new RuntimeException("Stub");
    }

    public void recreate() {
        throw new RuntimeException("Stub");
    }

    public void delete() {
        throw new RuntimeException("Stub");
    }
}
