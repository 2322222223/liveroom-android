package com.hyphenate.liveroom.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.hyphenate.EMCallBack;
import com.hyphenate.EMMessageListener;
import com.hyphenate.EMValueCallBack;
import com.hyphenate.chat.EMClient;
import com.hyphenate.chat.EMCmdMessageBody;
import com.hyphenate.chat.EMConferenceManager;
import com.hyphenate.chat.EMConferenceMember;
import com.hyphenate.chat.EMMessage;
import com.hyphenate.liveroom.Constant;
import com.hyphenate.liveroom.R;
import com.hyphenate.liveroom.entities.ChatRoom;
import com.hyphenate.liveroom.entities.RoomType;
import com.hyphenate.liveroom.manager.HttpRequestManager;
import com.hyphenate.liveroom.manager.PreferenceManager;
import com.hyphenate.liveroom.utils.AnimationUtil;
import com.hyphenate.liveroom.widgets.EaseTipDialog;
import com.hyphenate.util.EasyUtils;

import java.io.Serializable;
import java.util.List;

import static com.hyphenate.liveroom.Constant.EXTRA_PASSWORD;


public class ChatActivity extends BaseActivity {
    private static final String TAG = "ChatActivity";

    private String ownerName;
    private String roomName;
    private String textRoomId;
    private boolean isCreator;
    private boolean isAllowRequest;

    private ChatRoom chatRoom;
    private RoomType roomType = RoomType.COMMUNICATION;

    // 点赞或者礼物图片显示占位符
    private ImageView placeholder;
    private TextView roomTypeView;
    private TextView roomTypeDescView;

    private VoiceChatFragment voiceChatFragment;

    public static class Builder {
        private Intent intent;

        public Builder(Activity original) {
            intent = new Intent(original, ChatActivity.class);
            intent.putExtra(Constant.EXTRA_CHAT_TYPE, Constant.CHATTYPE_CHATROOM);
        }

        public Builder setPassword(String password) {
            intent.putExtra(EXTRA_PASSWORD, password);
            return this;
        }

        public Builder setChatRoomEntity(ChatRoom chatRoom) {
            intent.putExtra(Constant.EXTRA_CHAT_ROOM, chatRoom);
            return this;
        }

        public Builder setRoomType(String type) {
            intent.putExtra(Constant.EXTRA_ROOM_TYPE, type);
            return this;
        }

        public Intent build() {
            return intent;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_chat);

        chatRoom = (ChatRoom) getIntent().getSerializableExtra(Constant.EXTRA_CHAT_ROOM);
        ownerName = chatRoom.getOwnerName();
        isCreator = PreferenceManager.getInstance().getCurrentUsername().equalsIgnoreCase(ownerName);
        roomName = chatRoom.getRoomName();
        textRoomId = chatRoom.getRoomId();
        isAllowRequest = chatRoom.isAllowAudienceTalk();

        placeholder = findViewById(R.id.placeholder);
        roomTypeView = findViewById(R.id.tv_room_type);
        roomTypeDescView = findViewById(R.id.tv_type_desc);
        TextView roomNameView = findViewById(R.id.txt_room_name);
        TextView accountView = findViewById(R.id.txt_account);
        View tobeTalkerView = findViewById(R.id.iv_request_tobe_talker);

        roomType = RoomType.from(getIntent().getStringExtra(Constant.EXTRA_ROOM_TYPE));
        roomTypeView.setText(roomType.getName());
        roomTypeDescView.setText(roomType.getDesc());

        if (!isCreator) {
            tobeTalkerView.setVisibility(View.VISIBLE);
            tobeTalkerView.setOnClickListener((v) -> {
                if (!isAllowRequest) {
                    Toast.makeText(this, "该语聊房间不允许申请连麦 ~", Toast.LENGTH_SHORT).show();
                    return;
                }

                int result = voiceChatFragment.handleTalkerRequest();
                if (result == VoiceChatFragment.RESULT_NO_POSITION) {
                    new EaseTipDialog.Builder(this)
                            .setStyle(EaseTipDialog.TipDialogStyle.INFO)
                            .setTitle("提示")
                            .setMessage("该聊天室主播人数已满,请稍后重试 ...")
                            .addButton("确定", Constant.COLOR_BLACK, Constant.COLOR_WHITE, (dialog, view) -> {
                                dialog.dismiss();
                            })
                            .build()
                            .show();
                } else if (result == VoiceChatFragment.RESULT_NO_HANDLED) {
                    // 发送上麦申请
                    sendRequest(Constant.OP_REQUEST_TOBE_SPEAKER);
                }
            });
        }

        roomNameView.setText(roomName);
        accountView.setText(textRoomId);

        TextChatFragment textChatFragment = new TextChatFragment();
        textChatFragment.setArguments(getIntent().getExtras());
        textChatFragment.setOnEventCallback((operation, args) -> {
            Log.i(TAG, "OnEventCallback: " + operation);
            runOnUiThread(() -> {
                placeholder.setVisibility(View.VISIBLE);
                AnimationSet set = AnimationUtil.create();
                set.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        placeholder.setVisibility(View.GONE);
                        placeholder.setImageResource(0);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }
                });
                if (operation == TextChatFragment.MSG_FAVOURITE) {
                    placeholder.setImageResource(R.drawable.em_ic_favorite);
                } else if (operation == TextChatFragment.MSG_GIFT) {
                    placeholder.setImageResource(R.drawable.em_ic_giftcard);
                }
                placeholder.startAnimation(set);
            });
        });
        getSupportFragmentManager().beginTransaction().add(R.id.container_chat, textChatFragment).commit();

        voiceChatFragment = new VoiceChatFragment();
        voiceChatFragment.setArguments(getIntent().getExtras());
        voiceChatFragment.setOnEventCallback((op, args) -> {
            if (VoiceChatFragment.EVENT_TOBE_AUDIENCE == op) {
                // TODO: 发送下麦申请
                if (isCreator) { // 管理员下线其他主播
                    String username = (String) args[0];
                    grantRole(username, EMConferenceManager.EMConferenceRole.Audience);
                } else { // 主播向管理员发送下线的申请
                    sendRequest(Constant.OP_REQUEST_TOBE_AUDIENCE);
                }
            } else if (VoiceChatFragment.EVENT_ROOM_TYPE_CHANGED == op) {
                runOnUiThread(() -> {
                    roomType = (RoomType) args[0];
                    roomTypeView.setText(roomType.getName());
                    roomTypeDescView.setText(roomType.getDesc());
                });
            }
        });
        getSupportFragmentManager().beginTransaction().add(R.id.container_member, voiceChatFragment).commit();

        EMClient.getInstance().chatManager().addMessageListener(messageListener);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        String username = intent.getStringExtra(Constant.EXTRA_CHATROOM_ID);
        if (textRoomId.equals(username)) {
            super.onNewIntent(intent);
        } else {
            finish();
            startActivity(intent);
        }
    }

    @Override
    public void onBackPressed() {
        // hook back menu.
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EMClient.getInstance().chatManager().removeMessageListener(messageListener);
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_music:
                break;
            case R.id.btn_contacts:
                Intent i = new Intent(ChatActivity.this, MembersActivity.class);
                i.putExtra(Constant.EXTRA_CHATROOM_ID, textRoomId);
                startActivity(i);
                break;
            case R.id.btn_share:
                break;
            case R.id.btn_detail:
                startActivity(new RoomDetailsActivity.Builder(ChatActivity.this)
                        .setChatRoomEntity(chatRoom)
                        .setPassword(getIntent().getStringExtra(EXTRA_PASSWORD))
                        .setRoomType(roomType.getId())
                        .build());
                break;
            case R.id.btn_exit:
                if (!isCreator) { // 非语聊室创建者退出
                    finish();
                    return;
                }

                HttpRequestManager.getInstance().deleteChatRoom(textRoomId, new HttpRequestManager.IRequestListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        finish();
                    }

                    @Override
                    public void onFailed(int errCode, String desc) {
                        Toast.makeText(ChatActivity.this, errCode + " - " + desc, Toast.LENGTH_SHORT).show();
                    }
                });
                break;
        }
    }

    private void sendRequest(String op) {
        EMMessage msg = EMMessage.createSendMessage(EMMessage.Type.CMD);
        msg.addBody(new EMCmdMessageBody(""));
        msg.setTo(ownerName);
        msg.setAttribute(Constant.EM_CONFERENCE_OP, op);
        msg.setMessageStatusCallback(new EMCallBack() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "onSuccess: ");
            }

            @Override
            public void onError(int i, String s) {
                Log.i(TAG, "onError: " + i + " - " + s);
            }

            @Override
            public void onProgress(int i, String s) {
                Log.i(TAG, "onProgress: " + i + " - " + s);
            }
        });
        EMClient.getInstance().chatManager().sendMessage(msg);
    }

    private void grantRole(String username, EMConferenceManager.EMConferenceRole targetRole) {
        String jid = EasyUtils.getMediaRequestUid(EMClient.getInstance().getOptions().getAppKey(),
                username);
        EMClient.getInstance().conferenceManager().grantRole(
                "", new EMConferenceMember(jid, "", ""),
                targetRole, new EMValueCallBack<String>() {
                    @Override
                    public void onSuccess(String s) {
                        Log.i(TAG, "grantRole onSuccess: " + s);
                    }

                    @Override
                    public void onError(int i, String s) {
                        Log.i(TAG, "grantRole onError: " + i + " - " + s);
                    }
                }
        );
    }

    private EMMessageListener messageListener = new EMMessageListener() {
        @Override
        public void onMessageReceived(List<EMMessage> list) {
        }

        @Override
        public void onCmdMessageReceived(List<EMMessage> list) {
            for (EMMessage msg : list) {
                String operation = msg.getStringAttribute(Constant.EM_CONFERENCE_OP, null);
                Log.i(TAG, "onCmdMessageReceived: " + operation);
                if (Constant.OP_REQUEST_TOBE_SPEAKER.equals(operation)) {
                    boolean autoAgreeRequest = PreferenceManager.getInstance().isAutoAgree();
                    if (autoAgreeRequest) {
                        grantRole(msg.getFrom(), EMConferenceManager.EMConferenceRole.Talker);
                    } else {
                        runOnUiThread(() -> {
                            new EaseTipDialog.Builder(ChatActivity.this)
                                    .setStyle(EaseTipDialog.TipDialogStyle.INFO)
                                    .setTitle("提示")
                                    .setMessage(msg.getFrom() + " 申请上麦互动，同意吗？")
                                    .addButton("同意", Constant.COLOR_BLACK, Constant.COLOR_WHITE,
                                            (dialog, v) -> {
                                                dialog.dismiss();
                                                grantRole(msg.getFrom(), EMConferenceManager.EMConferenceRole.Talker);
                                            })
                                    .addButton("拒绝", Constant.COLOR_BLACK, Constant.COLOR_WHITE,
                                            (dialog, v) -> {
                                                dialog.dismiss();
                                                sendRequest(Constant.OP_REQUEST_TOBE_REJECTED);
                                            })
                                    .build()
                                    .show();
                        });
                    }
                } else if (Constant.OP_REQUEST_TOBE_AUDIENCE.equals(operation)) {
                    grantRole(msg.getFrom(), EMConferenceManager.EMConferenceRole.Audience);
                }
            }
        }

        @Override
        public void onMessageRead(List<EMMessage> list) {
        }

        @Override
        public void onMessageDelivered(List<EMMessage> list) {
        }

        @Override
        public void onMessageRecalled(List<EMMessage> list) {
        }

        @Override
        public void onMessageChanged(EMMessage emMessage, Object o) {
        }
    };
}
