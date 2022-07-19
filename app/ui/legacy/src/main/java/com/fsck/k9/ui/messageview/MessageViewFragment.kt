package com.fsck.k9.ui.messageview

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.IntentSender.SendIntentException
import android.os.Bundle
import android.os.Parcelable
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.fsck.k9.Account
import com.fsck.k9.K9
import com.fsck.k9.activity.MessageCompose
import com.fsck.k9.activity.MessageLoaderHelper
import com.fsck.k9.activity.MessageLoaderHelper.MessageLoaderCallbacks
import com.fsck.k9.activity.MessageLoaderHelperFactory
import com.fsck.k9.controller.MessageReference
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.fragment.AttachmentDownloadDialogFragment
import com.fsck.k9.fragment.ConfirmationDialogFragment
import com.fsck.k9.fragment.ConfirmationDialogFragment.ConfirmationDialogFragmentListener
import com.fsck.k9.helper.HttpsUnsubscribeUri
import com.fsck.k9.helper.MailtoUnsubscribeUri
import com.fsck.k9.helper.UnsubscribeUri
import com.fsck.k9.mail.Flag
import com.fsck.k9.mailstore.AttachmentViewInfo
import com.fsck.k9.mailstore.LocalMessage
import com.fsck.k9.mailstore.MessageViewInfo
import com.fsck.k9.preferences.AccountManager
import com.fsck.k9.ui.R
import com.fsck.k9.ui.base.ThemeManager
import com.fsck.k9.ui.choosefolder.ChooseFolderActivity
import com.fsck.k9.ui.messageview.CryptoInfoDialog.OnClickShowCryptoKeyListener
import com.fsck.k9.ui.messageview.MessageCryptoPresenter.MessageCryptoMvpView
import com.fsck.k9.ui.settings.account.AccountSettingsActivity
import com.fsck.k9.ui.share.ShareIntentBuilder
import com.fsck.k9.ui.withArguments
import com.fsck.k9.view.MessageCryptoDisplayStatus
import java.util.Locale
import org.koin.android.ext.android.inject
import timber.log.Timber

class MessageViewFragment :
    Fragment(),
    ConfirmationDialogFragmentListener,
    AttachmentViewCallback,
    OnClickShowCryptoKeyListener {

    private val themeManager: ThemeManager by inject()
    private val messageLoaderHelperFactory: MessageLoaderHelperFactory by inject()
    private val accountManager: AccountManager by inject()
    private val messagingController: MessagingController by inject()
    private val shareIntentBuilder: ShareIntentBuilder by inject()

    private lateinit var messageTopView: MessageTopView

    private var message: LocalMessage? = null
    private lateinit var messageLoaderHelper: MessageLoaderHelper
    private lateinit var messageCryptoPresenter: MessageCryptoPresenter
    private var showProgressThreshold: Long? = null
    private var preferredUnsubscribeUri: UnsubscribeUri? = null

    /**
     * Used to temporarily store the destination folder for refile operations if a confirmation
     * dialog is shown.
     */
    private var destinationFolderId: Long? = null
    private lateinit var fragmentListener: MessageViewFragmentListener

    private lateinit var account: Account
    lateinit var messageReference: MessageReference

    /**
     * `true` after [.onCreate] has been executed. This is used by `MessageList.configureMenu()` to make sure the
     * fragment has been initialized before it is used.
     */
    var isInitialized = false
        private set

    private var currentAttachmentViewInfo: AttachmentViewInfo? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)

        fragmentListener = try {
            activity as MessageViewFragmentListener
        } catch (e: ClassCastException) {
            throw ClassCastException("This fragment must be attached to a MessageViewFragmentListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)

        messageCryptoPresenter = MessageCryptoPresenter(messageCryptoMvpView)
        messageLoaderHelper = messageLoaderHelperFactory.createForMessageView(
            context = requireContext().applicationContext,
            loaderManager = loaderManager,
            fragmentManager = parentFragmentManager,
            callback = messageLoaderCallbacks
        )

        isInitialized = true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val messageViewThemeResourceId = themeManager.messageViewThemeResourceId
        val themedContext = ContextThemeWrapper(inflater.context, messageViewThemeResourceId)
        val layoutInflater = LayoutInflater.from(themedContext)

        val view = layoutInflater.inflate(R.layout.message, container, false)
        messageTopView = view.findViewById(R.id.message_view)

        initializeMessageTopView(messageTopView)

        return view
    }

    private fun initializeMessageTopView(messageTopView: MessageTopView) {
        messageTopView.setAttachmentCallback(this)
        messageTopView.setMessageCryptoPresenter(messageCryptoPresenter)

        messageTopView.setOnToggleFlagClickListener {
            onToggleFlagged()
        }

        messageTopView.setOnMenuItemClickListener { item ->
            onReplyMenuItemClicked(item.itemId)
        }

        messageTopView.setOnDownloadButtonClickListener {
            onDownloadButtonClicked()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val messageReference = MessageReference.parse(arguments?.getString(ARG_REFERENCE))
            ?: error("Invalid argument '$ARG_REFERENCE'")

        displayMessage(messageReference)
    }

    private fun displayMessage(messageReference: MessageReference) {
        Timber.d("MessageViewFragment displaying message %s", messageReference)

        this.messageReference = messageReference
        account = accountManager.getAccount(messageReference.accountUuid)
            ?: error("Account ${messageReference.accountUuid} not found")

        messageLoaderHelper.asyncStartOrResumeLoadingMessage(messageReference, null)

        invalidateMenu()
    }

    override fun onResume() {
        super.onResume()
        messageCryptoPresenter.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()

        if (requireActivity().isChangingConfigurations) {
            messageLoaderHelper.onDestroyChangingConfigurations()
        } else {
            messageLoaderHelper.onDestroy()
        }
    }

    private fun showMessage(messageViewInfo: MessageViewInfo) {
        hideKeyboard()

        val handledByCryptoPresenter = messageCryptoPresenter.maybeHandleShowMessage(
            messageTopView, account, messageViewInfo
        )

        if (!handledByCryptoPresenter) {
            messageTopView.showMessage(account, messageViewInfo)

            if (account.isOpenPgpProviderConfigured) {
                messageTopView.messageHeaderView.setCryptoStatusDisabled()
            } else {
                messageTopView.messageHeaderView.hideCryptoStatus()
            }
        }

        if (messageViewInfo.subject != null) {
            displaySubject(messageViewInfo.subject)
        }
    }

    private fun hideKeyboard() {
        val activity = activity ?: return

        val inputMethodManager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val decorView = activity.window.decorView
        inputMethodManager.hideSoftInputFromWindow(decorView.applicationWindowToken, 0)
    }

    private fun displayHeaderForLoadingMessage(message: LocalMessage) {
        val showStar = !isOutbox
        messageTopView.setHeaders(message, account, showStar)

        if (account.isOpenPgpProviderConfigured) {
            messageTopView.messageHeaderView.setCryptoStatusLoading()
        }

        displaySubject(message.subject)
        invalidateMenu()
    }

    private fun displaySubject(subject: String) {
        val displaySubject = subject.ifEmpty { getString(R.string.general_no_subject) }
        messageTopView.setSubject(displaySubject)
    }

    private fun onReplyMenuItemClicked(itemId: Int): Boolean {
        when (itemId) {
            R.id.reply -> onReply()
            R.id.reply_all -> onReplyAll()
            R.id.forward -> onForward()
            R.id.forward_as_attachment -> onForwardAsAttachment()
            R.id.share -> onSendAlternate()
            else -> error("Missing handler for reply menu item $itemId")
        }

        return true
    }

    private fun onDownloadButtonClicked() {
        messageTopView.disableDownloadButton()
        messageLoaderHelper.downloadCompleteMessage()
    }

    /**
     * Called from UI thread when user select Delete
     */
    fun onDelete() {
        val message = requireNotNull(message)

        if (K9.isConfirmDelete || K9.isConfirmDeleteStarred && message.isSet(Flag.FLAGGED)) {
            showDialog(R.id.dialog_confirm_delete)
        } else {
            delete()
        }
    }

    private fun delete() {
        // Disable the delete button after it has been tapped (to try to prevent accidental clicks)
        fragmentListener.disableDeleteAction()

        fragmentListener.showNextMessageOrReturn()

        messagingController.deleteMessage(messageReference)
    }

    private fun onRefile(destinationFolderId: Long?) {
        if (destinationFolderId == null || !messagingController.isMoveCapable(account)) {
            return
        }

        if (!messagingController.isMoveCapable(messageReference)) {
            Toast.makeText(activity, R.string.move_copy_cannot_copy_unsynced_message, Toast.LENGTH_LONG).show()
            return
        }

        if (destinationFolderId == account.spamFolderId && K9.isConfirmSpam) {
            this.destinationFolderId = destinationFolderId
            showDialog(R.id.dialog_confirm_spam)
        } else {
            refileMessage(destinationFolderId)
        }
    }

    private fun refileMessage(destinationFolderId: Long) {
        fragmentListener.showNextMessageOrReturn()

        val sourceFolderId = messageReference.folderId
        messagingController.moveMessage(account, sourceFolderId, messageReference, destinationFolderId)
    }

    fun onReply() {
        val message = this.message ?: return

        fragmentListener.onReply(
            messageReference = message.makeMessageReference(),
            decryptionResultForReply = messageCryptoPresenter.decryptionResultForReply
        )
    }

    fun onReplyAll() {
        val message = requireNotNull(this.message)

        fragmentListener.onReplyAll(
            messageReference = message.makeMessageReference(),
            decryptionResultForReply = messageCryptoPresenter.decryptionResultForReply
        )
    }

    fun onForward() {
        val message = requireNotNull(this.message)

        fragmentListener.onForward(
            messageReference = message.makeMessageReference(),
            decryptionResultForReply = messageCryptoPresenter.decryptionResultForReply
        )
    }

    fun onForwardAsAttachment() {
        val message = requireNotNull(this.message)

        fragmentListener.onForwardAsAttachment(
            messageReference = message.makeMessageReference(),
            decryptionResultForReply = messageCryptoPresenter.decryptionResultForReply
        )
    }

    fun onEditAsNewMessage() {
        val message = requireNotNull(this.message)

        fragmentListener.onEditAsNewMessage(message.makeMessageReference())
    }

    fun onMove() {
        require(messagingController.isMoveCapable(account))
        requireNotNull(message)

        if (!messagingController.isMoveCapable(messageReference)) {
            Toast.makeText(activity, R.string.move_copy_cannot_copy_unsynced_message, Toast.LENGTH_LONG).show()
            return
        }

        startRefileActivity(FolderOperation.MOVE, ACTIVITY_CHOOSE_FOLDER_MOVE)
    }

    fun onCopy() {
        require(messagingController.isCopyCapable(account))
        requireNotNull(message)

        if (!messagingController.isCopyCapable(messageReference)) {
            Toast.makeText(activity, R.string.move_copy_cannot_copy_unsynced_message, Toast.LENGTH_LONG).show()
            return
        }

        startRefileActivity(FolderOperation.COPY, ACTIVITY_CHOOSE_FOLDER_COPY)
    }

    fun onMoveToDrafts() {
        fragmentListener.showNextMessageOrReturn()

        val account = account
        val folderId = messageReference.folderId
        val messages = listOf(messageReference)
        messagingController.moveToDraftsFolder(account, folderId, messages)
    }

    fun onArchive() {
        onRefile(account.archiveFolderId)
    }

    fun onSpam() {
        onRefile(account.spamFolderId)
    }

    private fun startRefileActivity(operation: FolderOperation, requestCode: Int) {
        val action = if (operation == FolderOperation.MOVE) {
            ChooseFolderActivity.Action.MOVE
        } else {
            ChooseFolderActivity.Action.COPY
        }

        val intent = ChooseFolderActivity.buildLaunchIntent(
            context = requireActivity(),
            action = action,
            accountUuid = account.uuid,
            currentFolderId = messageReference.folderId,
            scrollToFolderId = account.lastSelectedFolderId,
            showDisplayableOnly = false,
            messageReference = messageReference
        )

        startActivityForResult(intent, requestCode)
    }

    fun onPendingIntentResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode and REQUEST_MASK_LOADER_HELPER == REQUEST_MASK_LOADER_HELPER) {
            val maskedRequestCode = requestCode xor REQUEST_MASK_LOADER_HELPER
            messageLoaderHelper.onActivityResult(maskedRequestCode, resultCode, data)
        } else if (requestCode and REQUEST_MASK_CRYPTO_PRESENTER == REQUEST_MASK_CRYPTO_PRESENTER) {
            val maskedRequestCode = requestCode xor REQUEST_MASK_CRYPTO_PRESENTER
            messageCryptoPresenter.onActivityResult(maskedRequestCode, resultCode, data)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) return

        when (requestCode) {
            REQUEST_CODE_CREATE_DOCUMENT -> onCreateDocumentResult(data)
            ACTIVITY_CHOOSE_FOLDER_MOVE -> onChooseFolderMoveResult(data)
            ACTIVITY_CHOOSE_FOLDER_COPY -> onChooseFolderCopyResult(data)
        }
    }

    private fun onCreateDocumentResult(data: Intent?) {
        if (data != null && data.data != null) {
            createAttachmentController(currentAttachmentViewInfo).saveAttachmentTo(data.data)
        }
    }

    private fun onChooseFolderMoveResult(data: Intent?) {
        if (data == null) return

        val destinationFolderId = data.getLongExtra(ChooseFolderActivity.RESULT_SELECTED_FOLDER_ID, -1L)
        val messageReferenceString = data.getStringExtra(ChooseFolderActivity.RESULT_MESSAGE_REFERENCE)
        val messageReference = MessageReference.parse(messageReferenceString)
        if (this.messageReference != messageReference) return

        account.setLastSelectedFolderId(destinationFolderId)

        fragmentListener.showNextMessageOrReturn()

        moveMessage(messageReference, destinationFolderId)
    }

    private fun onChooseFolderCopyResult(data: Intent?) {
        if (data == null) return

        val destinationFolderId = data.getLongExtra(ChooseFolderActivity.RESULT_SELECTED_FOLDER_ID, -1L)
        val messageReferenceString = data.getStringExtra(ChooseFolderActivity.RESULT_MESSAGE_REFERENCE)
        val messageReference = MessageReference.parse(messageReferenceString)
        if (this.messageReference != messageReference) return

        account.setLastSelectedFolderId(destinationFolderId)

        copyMessage(messageReference, destinationFolderId)
    }

    fun onSendAlternate() {
        val message = requireNotNull(message)

        val shareIntent = shareIntentBuilder.createShareIntent(message)
        val shareTitle = getString(R.string.send_alternate_chooser_title)
        val chooserIntent = Intent.createChooser(shareIntent, shareTitle)

        startActivity(chooserIntent)
    }

    fun onToggleRead() {
        toggleFlag(Flag.SEEN)
    }

    fun onToggleFlagged() {
        toggleFlag(Flag.FLAGGED)
    }

    private fun toggleFlag(flag: Flag) {
        require(!isOutbox)
        val message = requireNotNull(this.message)

        val newState = !message.isSet(flag)
        messagingController.setFlag(account, message.folder.databaseId, listOf(message), flag, newState)

        messageTopView.setHeaders(message, account, true)

        invalidateMenu()
    }

    private fun moveMessage(reference: MessageReference?, folderId: Long) {
        messagingController.moveMessage(account, messageReference.folderId, reference, folderId)
    }

    private fun copyMessage(reference: MessageReference?, folderId: Long) {
        messagingController.copyMessage(account, messageReference.folderId, reference, folderId)
    }

    private fun showDialog(dialogId: Int) {
        val fragment = when (dialogId) {
            R.id.dialog_confirm_delete -> {
                val title = getString(R.string.dialog_confirm_delete_title)
                val message = getString(R.string.dialog_confirm_delete_message)
                val confirmText = getString(R.string.dialog_confirm_delete_confirm_button)
                val cancelText = getString(R.string.dialog_confirm_delete_cancel_button)
                ConfirmationDialogFragment.newInstance(
                    dialogId, title, message,
                    confirmText, cancelText
                )
            }
            R.id.dialog_confirm_spam -> {
                val title = getString(R.string.dialog_confirm_spam_title)
                val message = resources.getQuantityString(R.plurals.dialog_confirm_spam_message, 1)
                val confirmText = getString(R.string.dialog_confirm_spam_confirm_button)
                val cancelText = getString(R.string.dialog_confirm_spam_cancel_button)
                ConfirmationDialogFragment.newInstance(
                    dialogId, title, message,
                    confirmText, cancelText
                )
            }
            R.id.dialog_attachment_progress -> {
                val currentAttachmentViewInfo = requireNotNull(this.currentAttachmentViewInfo)

                val message = getString(R.string.dialog_attachment_progress_title)
                val size = currentAttachmentViewInfo.size
                AttachmentDownloadDialogFragment.newInstance(size, message)
            }
            else -> {
                throw RuntimeException("Called showDialog(int) with unknown dialog id.")
            }
        }

        fragment.setTargetFragment(this, dialogId)
        fragment.show(parentFragmentManager, getDialogTag(dialogId))
    }

    private fun removeDialog(dialogId: Int) {
        if (!isAdded) return

        val fragmentManager = parentFragmentManager

        // Make sure the "show dialog" transaction has been processed when we call  findFragmentByTag() below.
        // Otherwise the fragment won't be found and the dialog will never be dismissed.
        fragmentManager.executePendingTransactions()

        val fragment = fragmentManager.findFragmentByTag(getDialogTag(dialogId)) as DialogFragment?
        fragment?.dismissAllowingStateLoss()
    }

    private fun getDialogTag(dialogId: Int): String {
        return String.format(Locale.US, "dialog-%d", dialogId)
    }

    override fun doPositiveClick(dialogId: Int) {
        if (dialogId == R.id.dialog_confirm_delete) {
            delete()
        } else if (dialogId == R.id.dialog_confirm_spam) {
            val destinationFolderId = requireNotNull(this.destinationFolderId)

            refileMessage(destinationFolderId)
            this.destinationFolderId = null
        }
    }

    override fun doNegativeClick(dialogId: Int) = Unit

    override fun dialogCancelled(dialogId: Int) = Unit

    val isOutbox: Boolean
        get() = messageReference.folderId == account.outboxFolderId

    val isMessageRead: Boolean
        get() = message?.isSet(Flag.SEEN) == true

    val isCopyCapable: Boolean
        get() = !isOutbox && messagingController.isCopyCapable(account)

    val isMoveCapable: Boolean
        get() = !isOutbox && messagingController.isMoveCapable(account)

    fun canMessageBeArchived(): Boolean {
        val archiveFolderId = account.archiveFolderId ?: return false
        return messageReference.folderId != archiveFolderId
    }

    fun canMessageBeMovedToSpam(): Boolean {
        val spamFolderId = account.spamFolderId ?: return false
        return messageReference.folderId != spamFolderId
    }

    fun canMessageBeUnsubscribed(): Boolean {
        return preferredUnsubscribeUri != null
    }

    fun onUnsubscribe() {
        val intent = when (val unsubscribeUri = preferredUnsubscribeUri) {
            is MailtoUnsubscribeUri -> {
                Intent(requireContext(), MessageCompose::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = unsubscribeUri.uri
                    putExtra(MessageCompose.EXTRA_ACCOUNT, messageReference.accountUuid)
                }
            }
            is HttpsUnsubscribeUri -> {
                Intent(Intent.ACTION_VIEW, unsubscribeUri.uri)
            }
            else -> error("Unknown UnsubscribeUri - $unsubscribeUri")
        }

        startActivity(intent)
    }

    fun disableAttachmentButtons(attachment: AttachmentViewInfo?) = Unit

    fun enableAttachmentButtons(attachment: AttachmentViewInfo?) = Unit

    fun runOnMainThread(runnable: Runnable) {
        requireActivity().runOnUiThread(runnable)
    }

    fun showAttachmentLoadingDialog() {
        showDialog(R.id.dialog_attachment_progress)
    }

    fun hideAttachmentLoadingDialogOnMainThread() {
        runOnMainThread {
            removeDialog(R.id.dialog_attachment_progress)
        }
    }

    fun refreshAttachmentThumbnail(attachment: AttachmentViewInfo?) {
        messageTopView.refreshAttachmentThumbnail(attachment)
    }

    private val messageCryptoMvpView: MessageCryptoMvpView = object : MessageCryptoMvpView {
        override fun redisplayMessage() {
            messageLoaderHelper.asyncReloadMessage()
        }

        @Throws(SendIntentException::class)
        override fun startPendingIntentForCryptoPresenter(
            intentSender: IntentSender,
            requestCode: Int?,
            fillIntent: Intent?,
            flagsMask: Int,
            flagValues: Int,
            extraFlags: Int
        ) {
            if (requestCode == null) {
                requireActivity().startIntentSender(intentSender, fillIntent, flagsMask, flagValues, extraFlags)
                return
            }

            val maskedRequestCode = requestCode or REQUEST_MASK_CRYPTO_PRESENTER
            requireActivity().startIntentSenderForResult(
                intentSender, maskedRequestCode, fillIntent, flagsMask, flagValues, extraFlags
            )
        }

        override fun showCryptoInfoDialog(displayStatus: MessageCryptoDisplayStatus, hasSecurityWarning: Boolean) {
            val dialog = CryptoInfoDialog.newInstance(displayStatus, hasSecurityWarning)
            dialog.setTargetFragment(this@MessageViewFragment, 0)
            dialog.show(parentFragmentManager, "crypto_info_dialog")
        }

        override fun restartMessageCryptoProcessing() {
            messageTopView.setToLoadingState()
            messageLoaderHelper.asyncRestartMessageCryptoProcessing()
        }

        override fun showCryptoConfigDialog() {
            AccountSettingsActivity.startCryptoSettings(requireActivity(), account.uuid)
        }
    }

    override fun onClickShowSecurityWarning() {
        messageCryptoPresenter.onClickShowCryptoWarningDetails()
    }

    override fun onClickSearchKey() {
        messageCryptoPresenter.onClickSearchKey()
    }

    override fun onClickShowCryptoKey() {
        messageCryptoPresenter.onClickShowCryptoKey()
    }

    interface MessageViewFragmentListener {
        fun onForward(messageReference: MessageReference, decryptionResultForReply: Parcelable?)
        fun onForwardAsAttachment(messageReference: MessageReference, decryptionResultForReply: Parcelable?)
        fun onEditAsNewMessage(messageReference: MessageReference)
        fun disableDeleteAction()
        fun onReplyAll(messageReference: MessageReference, decryptionResultForReply: Parcelable?)
        fun onReply(messageReference: MessageReference, decryptionResultForReply: Parcelable?)
        fun setProgress(enable: Boolean)
        fun showNextMessageOrReturn()
    }

    private val messageLoaderCallbacks: MessageLoaderCallbacks = object : MessageLoaderCallbacks {
        override fun onMessageDataLoadFinished(message: LocalMessage) {
            this@MessageViewFragment.message = message

            displayHeaderForLoadingMessage(message)
            messageTopView.setToLoadingState()
            showProgressThreshold = null
        }

        override fun onMessageDataLoadFailed() {
            Toast.makeText(activity, R.string.status_loading_error, Toast.LENGTH_LONG).show()
            showProgressThreshold = null
        }

        override fun onMessageViewInfoLoadFinished(messageViewInfo: MessageViewInfo) {
            showMessage(messageViewInfo)
            preferredUnsubscribeUri = messageViewInfo.preferredUnsubscribeUri
            showProgressThreshold = null
        }

        override fun onMessageViewInfoLoadFailed(messageViewInfo: MessageViewInfo) {
            showMessage(messageViewInfo)
            preferredUnsubscribeUri = null
            showProgressThreshold = null
        }

        override fun setLoadingProgress(current: Int, max: Int) {
            val oldShowProgressThreshold = showProgressThreshold

            if (oldShowProgressThreshold == null) {
                showProgressThreshold = SystemClock.elapsedRealtime() + PROGRESS_THRESHOLD_MILLIS
            } else if (oldShowProgressThreshold == 0L || SystemClock.elapsedRealtime() > oldShowProgressThreshold) {
                showProgressThreshold = 0L
                messageTopView.setLoadingProgress(current, max)
            }
        }

        override fun onDownloadErrorMessageNotFound() {
            messageTopView.enableDownloadButton()
            Toast.makeText(requireContext(), R.string.status_invalid_id_error, Toast.LENGTH_LONG).show()
        }

        override fun onDownloadErrorNetworkError() {
            messageTopView.enableDownloadButton()
            Toast.makeText(requireContext(), R.string.status_network_error, Toast.LENGTH_LONG).show()
        }

        override fun startIntentSenderForMessageLoaderHelper(
            intentSender: IntentSender,
            requestCode: Int,
            fillIntent: Intent?,
            flagsMask: Int,
            flagValues: Int,
            extraFlags: Int
        ) {
            showProgressThreshold = null
            try {
                val maskedRequestCode = requestCode or REQUEST_MASK_LOADER_HELPER
                requireActivity().startIntentSenderForResult(
                    intentSender, maskedRequestCode, fillIntent, flagsMask, flagValues, extraFlags
                )
            } catch (e: SendIntentException) {
                Timber.e(e, "Irrecoverable error calling PendingIntent!")
            }
        }
    }

    override fun onViewAttachment(attachment: AttachmentViewInfo) {
        currentAttachmentViewInfo = attachment

        createAttachmentController(attachment).viewAttachment()
    }

    override fun onSaveAttachment(attachment: AttachmentViewInfo) {
        currentAttachmentViewInfo = attachment

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = attachment.mimeType
            putExtra(Intent.EXTRA_TITLE, attachment.displayName)
            addCategory(Intent.CATEGORY_OPENABLE)
        }

        try {
            startActivityForResult(intent, REQUEST_CODE_CREATE_DOCUMENT)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.error_activity_not_found, Toast.LENGTH_LONG).show()
        }
    }

    private fun createAttachmentController(attachment: AttachmentViewInfo?): AttachmentController {
        return AttachmentController(requireContext(), messagingController, this, attachment)
    }

    private fun invalidateMenu() {
        requireActivity().invalidateMenu()
    }

    private enum class FolderOperation {
        COPY, MOVE
    }

    companion object {
        const val REQUEST_MASK_LOADER_HELPER = 1 shl 8
        const val REQUEST_MASK_CRYPTO_PRESENTER = 1 shl 9
        const val PROGRESS_THRESHOLD_MILLIS = 500 * 1000

        private const val ARG_REFERENCE = "reference"

        private const val ACTIVITY_CHOOSE_FOLDER_MOVE = 1
        private const val ACTIVITY_CHOOSE_FOLDER_COPY = 2
        private const val REQUEST_CODE_CREATE_DOCUMENT = 3

        fun newInstance(reference: MessageReference): MessageViewFragment {
            return MessageViewFragment().withArguments(
                ARG_REFERENCE to reference.toIdentityString()
            )
        }
    }
}
