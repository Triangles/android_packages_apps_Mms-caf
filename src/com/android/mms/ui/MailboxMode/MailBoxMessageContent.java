/*
 * Copyright (c) 2013-2014, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Copyright (C) 2012 The Android Open Source Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mms.ui;

import java.util.ArrayList;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SqliteWrapper;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;
import android.telephony.PhoneNumberUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.style.URLSpan;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.util.Log;
import android.util.TypedValue;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.android.mms.data.Contact;
import com.android.mms.LogTag;
import com.android.mms.R;
import com.android.mms.transaction.MessageSender;
import com.android.mms.transaction.MessagingNotification;
import com.android.mms.transaction.SmsMessageSender;
import com.android.mms.ui.MessageUtils;
import com.android.mms.ui.WwwContextMenuActivity;
import com.google.android.mms.MmsException;

public class MailBoxMessageContent extends Activity {
    private static final String TAG = "MailBoxMessageContent";
    private Uri mMessageUri;
    private int mMsgId;
    private long mMsgThreadId;// threadid of message
    private String mMsgText;// Text of message
    private String mMsgFrom;
    private String mFromtoLabel;
    private String mSendLabel;
    private String mDisplayName;
    private String mMsgTime;// Date of message
    private Long mDateLongFormat;
    private int mMsgstatus;
    private int mRead;
    private int mMailboxId;
    private int mMsgType = Sms.MESSAGE_TYPE_INBOX;
    private boolean mLock = false;

    private int mSubID = MessageUtils.SUB_INVALID;
    private Cursor mCursor = null;

    private TextView mBodyTextView;
    /*Operations for gesture to scale the current text fontsize of content*/
    private float mScaleFactor = 1;
    private  ScaleGestureDetector mScaleDetector;

    private static final int MENU_CALL_RECIPIENT = Menu.FIRST;
    private static final int MENU_DELETE = Menu.FIRST + 1;
    private static final int MENU_FORWARD = Menu.FIRST + 2;
    private static final int MENU_REPLY = Menu.FIRST + 3;
    private static final int MENU_RESEND = Menu.FIRST + 4;
    private static final int MENU_SAVE_TO_CONTACT = Menu.FIRST + 5;
    private static final int MENU_LOCK = Menu.FIRST + 6;

    private BackgroundDeleteHandler mBackgroundDeleteHandler;
    private static final int DELETE_MESSAGE_TOKEN = 6701;

    private static final int OPERATE_DEL_SINGLE_OVER = 1;
    private static final int UPDATE_TITLE = 2;
    private static final int SHOW_TOAST = 3;

    private SetReadThread mSetReadThread = null;
    private ContentResolver mContentResolver;
    private static final String[] SMS_LOCK_PROJECTION = {
        Sms._ID,
        Sms.LOCKED
    };
    private static final String[] SMS_DETAIL_PROJECTION = new String[] {
        Sms.THREAD_ID,
        Sms.DATE,
        Sms.ADDRESS,
        Sms.BODY,
        Sms.SUB_ID,
        Sms.LOCKED,
        Sms.DATE_SENT
    };

    private static final int COLUMN_THREAD_ID = 0;
    private static final int COLUMN_DATE = 1;
    private static final int COLUMN_SMS_ADDRESS = 2;
    private static final int COLUMN_SMS_BODY = 3;
    private static final int COLUMN_SMS_SUBID = 4;
    private static final int COLUMN_SMS_LOCKED = 5;
    private static final int COLUMN_DATE_SENT = 6;

    private static final int SMS_ADDRESS_INDEX = 0;
    private static final int SMS_BODY_INDEX = 1;
    private static final int SMS_SUB_ID_INDEX = 2;

    private float mFontSizeForSave = MessageUtils.FONT_SIZE_DEFAULT;

    private ArrayList<TextView> mSlidePaperItemTextViews;

    private class MyScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            mFontSizeForSave = MessageUtils.onFontSizeScale(mSlidePaperItemTextViews,
                    detector.getScaleFactor(), mFontSizeForSave);
            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setProgressBarIndeterminateVisibility(true);
        setContentView(R.layout.mailbox_msg_detail);
        mContentResolver = getContentResolver();
        mBackgroundDeleteHandler = new BackgroundDeleteHandler(mContentResolver);
        mSlidePaperItemTextViews = new ArrayList<TextView>();

        getIntentData();
        initUi();
    }

    @Override
    protected void onStop() {
        super.onStop();
        MessageUtils.saveTextFontSize(this, mFontSizeForSave);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mUiHandler.removeCallbacksAndMessages(null);
    }

    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getPointerCount() > 1) {
            mScaleDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onSearchRequested() {
        return false;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();

        if (MessageUtils.hasIccCard()) {
            menu.add(0, MENU_CALL_RECIPIENT, 0, R.string.menu_call)
                    .setIcon(R.drawable.ic_menu_call_holo_light)
                    .setTitle(R.string.menu_call)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }

        if (mMsgType == Sms.MESSAGE_TYPE_INBOX) {
            menu.add(0, MENU_REPLY, 0, R.string.menu_reply);
            menu.add(0, MENU_FORWARD, 0, R.string.menu_forward);
            menu.add(0, MENU_DELETE, 0, R.string.menu_delete_msg);
        } else if (mMsgType == Sms.MESSAGE_TYPE_FAILED
                || mMsgType == Sms.MESSAGE_TYPE_OUTBOX) {
            menu.add(0, MENU_FORWARD, 0, R.string.menu_forward);
            menu.add(0, MENU_RESEND, 0, R.string.menu_resend);
            menu.add(0, MENU_DELETE, 0, R.string.menu_delete_msg);
        } else if (mMsgType == Sms.MESSAGE_TYPE_SENT) {
            menu.add(0, MENU_FORWARD, 0, R.string.menu_forward);
            menu.add(0, MENU_RESEND, 0, R.string.menu_resend);
            menu.add(0, MENU_DELETE, 0, R.string.menu_delete_msg);
        } else if (mMsgType == Sms.MESSAGE_TYPE_QUEUED) {
            menu.add(0, MENU_FORWARD, 0, R.string.menu_forward);
            menu.add(0, MENU_DELETE, 0, R.string.menu_delete_msg);
        }

        if (isLockMessage()) {
            menu.add(0, MENU_LOCK, 0, R.string.menu_unlock);
        } else {
            menu.add(0, MENU_LOCK, 0, R.string.menu_lock);
        }

        if (!Contact.get(mMsgFrom, false).existsInDatabase()) {
            menu.add(0, MENU_SAVE_TO_CONTACT, 0, R.string.menu_add_to_contacts);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_CALL_RECIPIENT:
                if (MessageUtils.isMultiSimEnabledMms()) {
                    if (MessageUtils.getActivatedIccCardCount() > 1) {
                        showCallSelectDialog();
                    } else {
                        if (MessageUtils.isIccCardActivated(MessageUtils.SUB1)) {
                            MessageUtils.dialRecipient(this, mMsgFrom, MessageUtils.SUB1);
                        } else if (MessageUtils.isIccCardActivated(MessageUtils.SUB2)) {
                            MessageUtils.dialRecipient(this, mMsgFrom, MessageUtils.SUB2);
                        }
                    }
                } else {
                    MessageUtils.dialRecipient(this, mMsgFrom, MessageUtils.SUB_INVALID);
                }
                break;
            case MENU_DELETE:
                mLock = isLockMessage();
                DeleteMessageListener l = new DeleteMessageListener();
                confirmDeleteDialog(l, mLock);
                break;
            case MENU_FORWARD:
                Intent intentForward = new Intent(this, ComposeMessageActivity.class);
                intentForward.putExtra("sms_body", mMsgText);
                intentForward.putExtra("exit_on_sent", true);
                intentForward.putExtra("forwarded_message", true);
                this.startActivity(intentForward);
                break;
            case MENU_REPLY:
                Intent intentReplay = new Intent(this, ComposeMessageActivity.class);
                intentReplay.putExtra("reply_message", true);
                intentReplay.putExtra("address", mMsgFrom);
                intentReplay.putExtra("exit_on_sent", true);
                this.startActivity(intentReplay);
                break;
            case MENU_LOCK:
                lockUnlockMessage();
                break;
            case MENU_RESEND:
                resendShortMessage(mMsgThreadId, mMessageUri);
                finish();
                break;
            case MENU_SAVE_TO_CONTACT:
                saveToContact();
                break;
            case android.R.id.home:
                finish();
                break;
            default:
                return true;
        }

        return true;
    }

    private void confirmDeleteDialog(OnClickListener listener, boolean locked) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setMessage(locked ? R.string.confirm_delete_locked_message
                : R.string.confirm_delete_message);
        builder.setPositiveButton(R.string.delete, listener);
        builder.setNegativeButton(R.string.no, null);
        builder.show();
    }

    private void showCallSelectDialog() {
        String[] items = new String[MessageUtils.getActivatedIccCardCount()];
        for (int i = 0; i < items.length; i++) {
            items[i] = MessageUtils.getMultiSimName(this, i);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.menu_call));
        builder.setCancelable(true);
        builder.setItems(items, new DialogInterface.OnClickListener() {
            public final void onClick(DialogInterface dialog, int which) {
                MessageUtils.dialRecipient(MailBoxMessageContent.this, mMsgFrom, which);
                dialog.dismiss();
            }
        });
        builder.show();
    }

    public void saveToContact() {
        String address = mMsgFrom;
        if (TextUtils.isEmpty(address)) {
            if (LogTag.VERBOSE || Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
                Log.v(TAG, "  saveToContact fail for null address! ");
            }
            return;
        }

        // address must be a single recipient
        Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
        intent.setType(Contacts.CONTENT_ITEM_TYPE);
        if (Mms.isEmailAddress(address)) {
            intent.putExtra(ContactsContract.Intents.Insert.EMAIL, address);
        } else {
            intent.putExtra(ContactsContract.Intents.Insert.PHONE, address);
            intent.putExtra(ContactsContract.Intents.Insert.PHONE_TYPE,
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        this.startActivity(intent);
    }

    private void resendShortMessage(long threadId, Uri uri) {
        Cursor cursor = SqliteWrapper.query(this, getContentResolver(), uri, new String[] {
                Sms.ADDRESS, Sms.BODY, Sms.SUB_ID
        }, null, null, null);

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    MessageSender sender = new SmsMessageSender(this,
                            new String[] {cursor.getString(SMS_ADDRESS_INDEX)},
                            cursor.getString(SMS_BODY_INDEX),
                            threadId,
                            cursor.getInt(SMS_SUB_ID_INDEX));
                    sender.sendMessage(threadId);

                    // Delete the undelivered message since the sender will
                    // save a new one into database.
                    SqliteWrapper.delete(this, getContentResolver(), uri, null, null);
                }
            } catch (MmsException e) {
                Log.e(TAG, e.getMessage());
            } finally {
                cursor.close();
            }
        } else {
            Toast.makeText(MailBoxMessageContent.this, R.string.send_failure, Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private void lockUnlockMessage() {
        int lockValue;
        // 1, lock; 0, unlock
        mLock = isLockMessage();
        final Uri lockUri = mMessageUri;
        lockValue = mLock ? 0 : 1;
        final ContentValues values = new ContentValues(1);
        values.put("locked", lockValue);

        new Thread(new Runnable() {
            public void run() {
                Message msg = Message.obtain();
                msg.what = SHOW_TOAST;
                if (getContentResolver().update(lockUri, values, null, null) > 0) {
                    msg.obj = getString(R.string.operate_success);
                } else {
                    msg.obj = getString(R.string.operate_failure);
                }
                mUiHandler.sendMessage(msg);
            }
        }).start();
    }

    private boolean isLockMessage() {
        boolean locked = false;

        Cursor c = SqliteWrapper.query(MailBoxMessageContent.this, mContentResolver, mMessageUri,
                SMS_LOCK_PROJECTION, null, null, null);

        try {
            if (c != null && c.moveToFirst()) {
                locked = c.getInt(1) != 0;
            }
        } finally {
            if (c != null) c.close();
        }
        return locked;
    }

    private void getIntentData() {
        Intent intent = getIntent();
        mMessageUri = (Uri) intent.getParcelableExtra("msg_uri");
        mMsgThreadId = intent.getLongExtra("sms_threadid", -1);
        mMsgText = intent.getStringExtra("sms_body");
        mMsgFrom = intent.getStringExtra("sms_fromto");
        mFromtoLabel = intent.getStringExtra("sms_fromtolabel");
        mSendLabel = intent.getStringExtra("sms_sendlabel");
        mDisplayName = intent.getStringExtra("sms_displayname");
        mDateLongFormat = intent.getLongExtra("sms_datelongformat", -1);
        mMsgstatus = intent.getIntExtra("sms_status", -1);
        mRead = intent.getIntExtra("sms_read", 0);
        mMailboxId = intent.getIntExtra("mailboxId", Sms.MESSAGE_TYPE_INBOX);
        mLock = intent.getIntExtra("sms_locked", 0) != 0;
        mSubID = intent.getIntExtra("sms_subid", MessageUtils.SUB_INVALID);
        mMsgTime = MessageUtils.formatTimeStampString(this, mDateLongFormat);
        mMsgType = intent.getIntExtra("sms_type", Sms.MESSAGE_TYPE_INBOX);
    }

    private void initUi() {
        setProgressBarIndeterminateVisibility(true);

        mScaleDetector = new ScaleGestureDetector(this, new MyScaleListener());

        mBodyTextView = (TextView) findViewById(R.id.textViewBody);
        mBodyTextView
                .setTextSize(TypedValue.COMPLEX_UNIT_PX, MessageUtils.getTextFontSize(this));
        mBodyTextView.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
        mBodyTextView.setTextIsSelectable(true);
        mBodyTextView.setText(mMsgText);
        mBodyTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MessageUtils.onMessageContentClick(MailBoxMessageContent.this, mBodyTextView);
            }
        });
        mSlidePaperItemTextViews.add(mBodyTextView);
        TextView mFromTextView = (TextView) findViewById(R.id.textViewFrom);
        mFromTextView.setText(mFromtoLabel);
        TextView mNumberView = (TextView) findViewById(R.id.textViewNumber);
        if (MessageUtils.isWapPushNumber(mMsgFrom)) {
            String[] mAddresses = mMsgFrom.split(":");
            mNumberView.setText(mAddresses[getResources().getInteger(
                    R.integer.wap_push_address_index)]);
        } else {
            mNumberView.setText(mMsgFrom);
        }
        TextView mTimeTextView = (TextView) findViewById(R.id.textViewTime);
        mTimeTextView.setText(mSendLabel);
        TextView mTimeDetailTextView = (TextView) findViewById(R.id.textViewTimeDetail);
        mTimeDetailTextView.setText(mMsgTime);
        TextView mSlotTypeView = (TextView) findViewById(R.id.textViewSlotType);


        if (MessageUtils.isMultiSimEnabledMms()) {
            mSlotTypeView.setVisibility(View.VISIBLE);
            mSlotTypeView.setText(getString(R.string.slot_type,
                    MessageUtils.getMultiSimName(this, mSubID)));
        }

        if (!TextUtils.isEmpty(mDisplayName) && !mDisplayName.equals(mMsgFrom)) {
            if (MessageUtils.isWapPushNumber(mDisplayName)) {
                String[] mName = mDisplayName.split(":");
                mNumberView.setText(mName[getResources().getInteger(
                        R.integer.wap_push_address_index)]);
            } else {
                String numberStr = mDisplayName + " <" + mMsgFrom + ">";
                mNumberView.setText(numberStr);
            }
        }

        if (mRead == 0) {
            if (mSetReadThread == null) {
                mSetReadThread = new SetReadThread();
            }
            mSetReadThread.start();
        } else {
            setProgressBarIndeterminateVisibility(false);
        }

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
    }

    private class DeleteMessageListener implements OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int whichButton) {
            dialog.dismiss();

            new AsyncTask<Void, Void, Void>() {
                protected Void doInBackground(Void... none) {
                    mBackgroundDeleteHandler.startDelete(DELETE_MESSAGE_TOKEN, null, mMessageUri,
                            mLock ? null : "locked=0", null);
                    return null;
                }
            }.execute();
        }
    }

    private final class BackgroundDeleteHandler extends AsyncQueryHandler {
        public BackgroundDeleteHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        @Override
        protected void onDeleteComplete(int token, Object cookie, int result) {
            switch (token) {
                case DELETE_MESSAGE_TOKEN:
                    Message msg = Message.obtain();
                    msg.what = OPERATE_DEL_SINGLE_OVER;
                    msg.arg1 = result;
                    mUiHandler.sendMessage(msg);
                    break;
            }
        }
    }

    private Handler mUiHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE_TITLE:
                    setProgressBarIndeterminateVisibility(false);
                    break;
                case SHOW_TOAST:
                    String toastStr = (String) msg.obj;
                    Toast.makeText(MailBoxMessageContent.this, toastStr,
                            Toast.LENGTH_SHORT).show();
                    break;
                case OPERATE_DEL_SINGLE_OVER:
                    int result = msg.arg1;
                    if (result > 0) {
                        Toast.makeText(MailBoxMessageContent.this,
                                R.string.operate_success, Toast.LENGTH_SHORT)
                                .show();
                    } else {
                        Toast.makeText(MailBoxMessageContent.this,
                                R.string.operate_failure, Toast.LENGTH_SHORT)
                                .show();
                    }
                    finish();
                default:
                    break;
            }
        }
    };

    private class SetReadThread extends Thread {
        public SetReadThread() {
            super("SetReadThread");
        }

        public void run() {
            try {
                ContentValues values = new ContentValues(2);
                values.put(Sms.READ, MessageUtils.MESSAGE_READ);
                values.put(Sms.SEEN, MessageUtils.MESSAGE_SEEN);
                SqliteWrapper.update(MailBoxMessageContent.this, getContentResolver(),
                        mMessageUri, values, null, null);
                MessagingNotification.nonBlockingUpdateNewMessageIndicator(
                        MailBoxMessageContent.this, MessagingNotification.THREAD_NONE, false);
            } catch (Exception e) {
            }
            Message msg = Message.obtain();
            msg.what = UPDATE_TITLE;
            mUiHandler.sendMessage(msg);
        }
    }
}
