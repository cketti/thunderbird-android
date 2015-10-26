package com.fsck.k9.ui.setup;


import android.content.Context;

import com.fsck.k9.Account;
import com.fsck.k9.helper.EmailHelper;
import com.fsck.k9.remote.eas.EasBackend;
import com.fsck.k9.ui.AccountLoaderAsyncTask;
import com.fsck.k9.ui.AccountLoaderAsyncTask.AccountLoaderCallback;


class EasServerSettingsPresenter implements AccountLoaderCallback {
    private final Context context;
    private EasServerSettingsView view;
    private Account account;


    EasServerSettingsPresenter(Context context, EasServerSettingsView view) {
        this.context = context;
        this.view = view;
    }

    public static EasServerSettingsPresenter create(Context context, EasServerSettingsView view) {
        return new EasServerSettingsPresenter(context, view);
    }

    public void onDestroy() {
        view = null;
    }

    public void initViews(boolean restoringState, String accountUuid, String initialEmail, String initialPassword) {
        loadAccountAndShowLoading(accountUuid);

        if (!restoringState) {
            setInitialInputs(initialEmail, initialPassword);
        }

        validateInputsAndEnableNextButton();
    }

    public void onInputChanged() {
        validateInputsAndEnableNextButton();
    }

    public void onNextButtonClicked() {
        String hostname = view.getHostname();
        String username = view.getUsername();
        String password = view.getPassword();

        boolean inputOkay = areInputsValid(hostname, username, password);
        if (!inputOkay) {
            throw new AssertionError("Invalid settings");
        }

        setupAccount(hostname, username, password);
        view.startAccountSetupNames(account);
    }

    private void setInitialInputs(String initialEmail, String initialPassword) {
        String domain = EmailHelper.getDomainFromEmailAddress(initialEmail);
        String initialHostname = "m." + domain;

        view.setHostname(initialHostname);
        view.setUsername(initialEmail);
        view.setPassword(initialPassword);
    }

    private void validateInputsAndEnableNextButton() {
        String hostname = view.getHostname();
        String username = view.getUsername();
        String password = view.getPassword();

        boolean enableNextButton = isAccountLoaded() && areInputsValid(hostname, username, password);
        view.setNextButtonEnabled(enableNextButton);
    }

    private boolean areInputsValid(String hostname, String username, String password) {
        return !hostname.isEmpty() && !username.isEmpty() && !password.isEmpty();
    }

    private void loadAccountAndShowLoading(String accountUuid) {
        view.setShowLoading(true);
        loadAccount(accountUuid);
    }

    void loadAccount(String accountUuid) {
        AccountLoaderAsyncTask.loadAccount(context, accountUuid, this);
    }

    @Override
    public void onAccountLoaded(Account account) {
        if (isDestroyed()) {
            return;
        }

        this.account = account;
        view.setShowLoading(false);
        validateInputsAndEnableNextButton();
    }

    private boolean isAccountLoaded() {
        return account != null;
    }

    private boolean isDestroyed() {
        return view == null;
    }

    private void setupAccount(String hostname, String username, String password) {
        String easStoreUri = EasBackend.createEasStoreUri(hostname, username, password);
        account.setStoreUri(easStoreUri);
        account.setTransportUri(easStoreUri);
    }
}
