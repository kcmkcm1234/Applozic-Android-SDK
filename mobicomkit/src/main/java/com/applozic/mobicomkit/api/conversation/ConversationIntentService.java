package com.applozic.mobicomkit.api.conversation;

import android.app.IntentService;
import android.content.Intent;

import com.applozic.mobicomkit.api.account.user.UserService;
import com.applozic.mobicommons.commons.core.utils.Utils;
import com.applozic.mobicommons.people.channel.Channel;
import com.applozic.mobicommons.people.contact.Contact;

import java.util.List;

/**
 * Created by devashish on 15/12/13.
 */
public class ConversationIntentService extends IntentService {

    public static final String SYNC = "AL_SYNC";
    private static final String TAG = "ConversationIntent";
    public static final String MESSAGE_METADATA_UPDATE = "MessageMetadataUpdate";
    public static final String MUTED_USER_LIST_SYNC = "MutedUserListSync";
    private static final int PRE_FETCH_MESSAGES_FOR = 6;
    private MobiComMessageService mobiComMessageService;

    public ConversationIntentService() {
        super("ConversationIntent");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.mobiComMessageService = new MobiComMessageService(this, MessageIntentService.class);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        boolean sync = intent.getBooleanExtra(SYNC, false);
        boolean metadataSync = intent.getBooleanExtra(MESSAGE_METADATA_UPDATE, false);
        boolean mutedUserListSync = intent.getBooleanExtra(MUTED_USER_LIST_SYNC, false);

        if (mutedUserListSync) {
            Utils.printLog(ConversationIntentService.this, TAG, "Muted user list sync started..");
            new Thread(new MutedUserListSync()).start();
            return;
        }

        if (metadataSync) {
            Utils.printLog(ConversationIntentService.this, TAG, "Syncing messages service started for metadata update");
            mobiComMessageService.syncMessageForMetadataUpdate();
            return;
        }

        Utils.printLog(ConversationIntentService.this, TAG, "Syncing messages service started: " + sync);

        if (sync) {
            mobiComMessageService.syncMessages();
        } else {
            Thread thread = new Thread(new ConversationSync());
            thread.start();
        }
    }

    private class ConversationSync implements Runnable {

        public ConversationSync() {
        }

        @Override
        public void run() {
            try {
                MobiComConversationService mobiComConversationService = new MobiComConversationService(ConversationIntentService.this);
                List<Message> messages = mobiComConversationService.getLatestMessagesGroupByPeople();
                UserService.getInstance(ConversationIntentService.this).processSyncUserBlock();
                for (Message message : messages.subList(0, Math.min(PRE_FETCH_MESSAGES_FOR, messages.size()))) {
                    Contact contact = null;
                    Channel channel = null;

                    if (message.getGroupId() != null) {
                        channel = new Channel(message.getGroupId());
                    } else {
                        contact = new Contact(message.getContactIds());
                    }

                    mobiComConversationService.getMessages(1L, null, contact, channel, null, true);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class MutedUserListSync implements Runnable {
        @Override
        public void run() {
            try {
                UserService.getInstance(ConversationIntentService.this).getMutedUserList();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
