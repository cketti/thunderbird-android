package com.fsck.k9.backend.eas;


import android.content.Context;

import com.fsck.k9.mail.AuthenticationFailedException;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.protocol.eas.Account;
import com.fsck.k9.protocol.eas.EasFolderSync;
import com.fsck.k9.protocol.eas.EasOperation;
import com.fsck.k9.protocol.eas.EasProvision;
import com.fsck.k9.protocol.eas.HostAuth;
import com.fsck.k9.protocol.eas.PolicyManager;
import com.fsck.k9.protocol.eas.callback.FolderSyncCallback;


public class EasSettingsChecker {
    private final Context context;
    private final String deviceId;
    private final boolean forceProvisionCommand;
    private final Account account;


    public EasSettingsChecker(Context context, String emailAddress, String username, String password,
            String hostname, String deviceId, boolean forceProvisionCommand) {
        this.context = context;
        this.deviceId = deviceId;
        this.forceProvisionCommand = forceProvisionCommand;

        account = createEasAccount(emailAddress, username, password, hostname);
    }

    public void checkServerSettings() throws MessagingException {
        if (forceProvisionCommand) {
            provision();
        } else {
            folderSync();
        }
    }

    private void provision() throws MessagingException {
        EasProvision easProvision = new EasProvision(context, account);
        int result = easProvision.provision();

        handleResult(result);
    }

    private void folderSync() throws MessagingException {
        EasFolderSync folderSync = new EasFolderSync(context, account, new NoOpFolderSyncCallback());
        int result = folderSync.performOperation();

        handleResult(result);
    }

    private void handleResult(int result) throws MessagingException {
        if (result == EasOperation.RESULT_AUTHENTICATION_ERROR) {
            throw new AuthenticationFailedException("Authentication failed");
        }

        if (result == EasOperation.RESULT_FORBIDDEN) {
            throw new AccessDeniedException();
        }

        if (result == EasOperation.RESULT_ABORT) {
            // If we end up here authentication succeeded. We'll handle provisioning later.
            return;
        }

        if (result < EasOperation.RESULT_MIN_OK_RESULT) {
            throw new MessagingException("Couldn't validate server settings");
        }
    }

    private Account createEasAccount(String emailAddress, String username, String password, String hostname) {
        HostAuth hostAuth = new HostAuth();
        hostAuth.mLogin = username;
        hostAuth.mPassword = password;
        hostAuth.mAddress = hostname;
        hostAuth.mFlags = HostAuth.FLAG_SSL;

        Account easAccount = new DummyAccount();
        easAccount.mHostAuthRecv = hostAuth;
        easAccount.mEmailAddress = emailAddress;

        return easAccount;
    }


    static class NoOpFolderSyncCallback implements FolderSyncCallback {
        @Override
        public void addFolder(String serverId, String name, int type, String parentServerId) {
            // Do nothing
        }

        @Override
        public void removeFolder(String serverId) {
            // Do nothing
        }

        @Override
        public void changeFolder(String serverId, String name, int type, String parentServerId) {
            // Do nothing
        }

        @Override
        public void clearFolders() {
            // Do nothing
        }

        @Override
        public void commitFolderChanges() {
            // Do nothing
        }
    }


    private class DummyAccount extends Account {
        @Override
        public String getDeviceId() {
            return deviceId;
        }

        @Override
        public boolean shouldHandleFullProvisioning() {
            return false;
        }

        @Override
        public void remoteWipe() {
            // Do nothing
        }

        @Override
        public PolicyManager getPolicyManager() {
            throw new UnsupportedOperationException();
        }
    }
}
