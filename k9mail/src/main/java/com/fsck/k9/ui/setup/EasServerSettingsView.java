package com.fsck.k9.ui.setup;


import com.fsck.k9.Account;


interface EasServerSettingsView {
    String getHostname();
    String getUsername();
    String getPassword();

    void setHostname(String hostname);
    void setUsername(String username);
    void setPassword(String password);
    void setShowLoading(boolean enabled);
    void setNextButtonEnabled(boolean enabled);

    void startAccountSetupNames(Account account);
}
