package com.fsck.k9.ui.setup;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.fsck.k9.Account;
import com.fsck.k9.R;
import com.fsck.k9.activity.K9Activity;
import com.fsck.k9.activity.setup.AccountSetupNames;
import com.fsck.k9.helper.SimpleTextWatcher;


public class EasServerSettingsActivity extends K9Activity implements EasServerSettingsView, OnClickListener {
    private static final String EXTRA_ACCOUNT_UUID = "accountUuid";
    private static final String EXTRA_EMAIL = "email";
    private static final String EXTRA_PASSWORD = "password";


    private EasServerSettingsPresenter presenter;
    private EditText hostnameInput;
    private EditText usernameInput;
    private EditText passwordInput;
    private Button nextButton;


    public static void start(Activity activity, Account account, String email, String password) {
        Intent intent = new Intent(activity, EasServerSettingsActivity.class);
        intent.putExtra(EXTRA_ACCOUNT_UUID, account.getUuid());
        intent.putExtra(EXTRA_EMAIL, email);
        intent.putExtra(EXTRA_PASSWORD, password);

        activity.startActivity(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.setup_eas_server_settings);

        findViews();
        setupViews();

        presenter = EasServerSettingsPresenter.create(getApplicationContext(), this);
        boolean restoringState = (savedInstanceState != null);
        initializePresenter(getIntent(), restoringState);
    }

    @Override
    protected void onDestroy() {
        presenter.onDestroy();
        super.onDestroy();
    }

    private void initializePresenter(Intent intent, boolean restoringState) {
        String accountUuid = intent.getStringExtra(EXTRA_ACCOUNT_UUID);
        String initialEmail = intent.getStringExtra(EXTRA_EMAIL);
        String initialPassword = intent.getStringExtra(EXTRA_PASSWORD);

        presenter.initViews(restoringState, accountUuid, initialEmail, initialPassword);
    }

    private void findViews() {
        hostnameInput = (EditText) findViewById(R.id.setup_eas_hostname);
        usernameInput = (EditText) findViewById(R.id.setup_eas_username);
        passwordInput = (EditText) findViewById(R.id.setup_eas_password);
        nextButton = (Button) findViewById(R.id.next);
    }

    private void setupViews() {
        TextChangedWatcher textChangedWatcher = new TextChangedWatcher();
        hostnameInput.addTextChangedListener(textChangedWatcher);
        usernameInput.addTextChangedListener(textChangedWatcher);
        passwordInput.addTextChangedListener(textChangedWatcher);
        passwordInput.setOnEditorActionListener(new InputDoneActionListener());

        nextButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (view == nextButton) {
            presenter.onNextButtonClicked();
        }
    }

    @Override
    public String getHostname() {
        return hostnameInput.getText().toString();
    }

    @Override
    public void setHostname(String hostname) {
        hostnameInput.setText(hostname);
    }

    @Override
    public String getUsername() {
        return usernameInput.getText().toString();
    }

    @Override
    public void setUsername(String username) {
        usernameInput.setText(username);
    }

    @Override
    public String getPassword() {
        return passwordInput.getText().toString();
    }

    @Override
    public void setPassword(String password) {
        passwordInput.setText(password);
    }

    @Override
    public void setShowLoading(boolean enabled) {
        setProgressBarIndeterminateVisibility(enabled);
    }

    @Override
    public void setNextButtonEnabled(boolean enabled) {
        nextButton.setEnabled(enabled);
    }

    @Override
    public void startAccountSetupNames(Account account) {
        AccountSetupNames.actionSetNames(this, account);
        finish();
    }


    private class TextChangedWatcher extends SimpleTextWatcher {
        @Override
        public void afterTextChanged(Editable text) {
            presenter.onInputChanged();
        }
    }

    private class InputDoneActionListener implements OnEditorActionListener {
        @Override
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            if (actionId != EditorInfo.IME_ACTION_DONE) {
                return false;
            }

            if (nextButton.isEnabled()) {
                nextButton.performClick();
            }
            return true;
        }
    }
}
