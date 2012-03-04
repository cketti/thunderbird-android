
package com.fsck.k9.controller;

import android.content.Context;
import com.fsck.k9.Account;
import com.fsck.k9.AccountStats;
import com.fsck.k9.BaseAccount;
import com.fsck.k9.mail.Folder;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.Part;

import java.util.List;

/**
 * Defines the interface that {@link MessagingController} will use to callback to requesters.
 *
 * <p>
 * This class is defined as non-abstract so that someone who wants to receive only a few messages
 * can do so without implementing the entire interface. It is highly recommended that users of this
 * interface use the {@code @Override} annotation in their implementations to avoid being caught by
 * changes in this class.
 * </p>
 */
public class MessagingListener {
    public void searchStats(AccountStats stats) {}


    public void accountStatusChanged(BaseAccount account, AccountStats stats) {}

    public void accountSizeChanged(Account account, long oldSize, long newSize) {}


    public void listFoldersStarted(Account account) {}

    public void listFolders(Account account, Folder[] folders) {}

    public void listFoldersFinished(Account account) {}

    public void listFoldersFailed(Account account, String message) {}


    public void listLocalMessagesStarted(Account account, String folder) {}

    public void listLocalMessages(Account account, String folder, Message[] messages) {}

    public void listLocalMessagesAddMessages(Account account, String folder,
            List<Message> messages) {}

    public void listLocalMessagesUpdateMessage(Account account, String folder, Message message) {}

    public void listLocalMessagesRemoveMessage(Account account, String folder, Message message) {}

    public void listLocalMessagesFinished(Account account, String folder) {}

    public void listLocalMessagesFailed(Account account, String folder, String message) {}


    public void synchronizeMailboxStarted(Account account, String folder) {}

    public void synchronizeMailboxHeadersStarted(Account account, String folder) {}

    public void synchronizeMailboxHeadersProgress(Account account, String folder,
            int completed, int total) {}

    public void synchronizeMailboxHeadersFinished(Account account, String folder,
            int totalMessagesInMailbox, int numNewMessages) {}

    public void synchronizeMailboxProgress(Account account, String folder, int completed,
            int total) {}

    public void synchronizeMailboxNewMessage(Account account, String folder, Message message) {}

    public void synchronizeMailboxAddOrUpdateMessage(Account account, String folder,
            Message message) {}

    public void synchronizeMailboxRemovedMessage(Account account, String folder,
            Message message) {}

    public void synchronizeMailboxFinished(Account account, String folder,
            int totalMessagesInMailbox, int numNewMessages) {}

    public void synchronizeMailboxFailed(Account account, String folder, String message) {}


    public void loadMessageForViewStarted(Account account, String folder, String uid) {}

    public void loadMessageForViewHeadersAvailable(Account account, String folder, String uid,
            Message message) {}

    public void loadMessageForViewBodyAvailable(Account account, String folder, String uid,
            Message message) {}

    public void loadMessageForViewFinished(Account account, String folder, String uid,
            Message message) {}

    public void loadMessageForViewFailed(Account account, String folder, String uid,
            Throwable t) {}

    /**
     * Called when a message for view has been fully displayed on the screen.
     */
    public void messageViewFinished() {}


    public void checkMailStarted(Context context, Account account) {}

    public void checkMailFinished(Context context, Account account) {}

    public void checkMailFailed(Context context, Account account, String reason) {}


    public void sendPendingMessagesStarted(Account account) {}

    public void sendPendingMessagesCompleted(Account account) {}

    public void sendPendingMessagesFailed(Account account) {}


    public void emptyTrashCompleted(Account account) {}


    public void folderStatusChanged(Account account, String folderName, int unreadMessageCount) {}


    /**
     * Called when starting to rename a folder.
     *
     * @param account
     *         The account the folder belongs to.
     * @param oldFolderName
     *         The name of the folder that is going to be renamed.
     * @param newFolderName
     *         The new folder name.
     */
    public void folderRenameStarted(Account account, String oldFolderName, String newFolderName) {
        /* do nothing */
    }

    /**
     * Called when a folder was successfully renamed.
     *
     * @param account
     *         The account the folder belongs to.
     * @param oldFolderName
     *         The old folder name.
     * @param newFolderName
     *         The new folder name.
     */
    public void folderRenameFinished(Account account, String oldFolderName, String newFolderName) {
        /* do nothing */
    }

    /**
     * Called when renaming a folder failed.
     *
     * @param account
     *         The account the folder belongs to.
     * @param oldFolderName
     *         The folder name.
     * @param newFolderName
     *         The new name for the folder that couldn't be used.
     */
    public void folderRenameFailed(Account account, String oldFolderName, String newFolderName) {
        /* do nothing */
    }


    /**
     * Called when starting to delete a folder.
     *
     * @param account
     *         The account the folder belongs to.
     * @param folderName
     *         The name of the folder that is going to be deleted.
     */
    public void folderDeleteStarted(Account account, String folderName) { /* do nothing */ }

    /**
     * Called when a folder was successfully deleted.
     *
     * @param account
     *         The account the folder belongs to.
     * @param folderName
     *         The name of the folder that was deleted.
     */
    public void folderDeleteFinished(Account account, String folderName) { /* do nothing */ }

    /**
     * Called when deleting a folder failed.
     *
     * @param account
     *         The account the folder belongs to.
     * @param folderName
     *         The name of the folder that couldn't be deleted.
     */
    public void folderDeleteFailed(Account account, String folderName) { /* do nothing */ }


    public void systemStatusChanged() {}


    public void messageDeleted(Account account, String folder, Message message) {}

    public void messageUidChanged(Account account, String folder, String oldUid, String newUid) {}


    public void setPushActive(Account account, String folderName, boolean enabled) {}


    public void loadAttachmentStarted(Account account, Message message, Part part, Object tag,
            boolean requiresDownload) {}

    public void loadAttachmentFinished(Account account, Message message, Part part, Object tag) {}

    public void loadAttachmentFailed(Account account, Message message, Part part, Object tag,
            String reason) {}



    public void pendingCommandStarted(Account account, String commandTitle) {}

    public void pendingCommandsProcessing(Account account) {}

    public void pendingCommandCompleted(Account account, String commandTitle) {}

    public void pendingCommandsFinished(Account account) {}


    /**
     * General notification messages subclasses can override to be notified that the controller
     * has completed a command. This is useful for turning off progress indicators that may have
     * been left over from previous commands.
     *
     * @param moreCommandsToRun
     *         {@code true} if the controller will continue on to another command immediately.
     *         {@code false} otherwise.
     */
    public void controllerCommandCompleted(boolean moreCommandsToRun) {}
}
