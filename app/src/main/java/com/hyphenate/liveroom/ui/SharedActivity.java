package com.hyphenate.liveroom.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.hyphenate.liveroom.Constant;
import com.hyphenate.liveroom.R;
import com.hyphenate.liveroom.utils.SystemShareUtils;

public class SharedActivity extends BaseActivity {

    private ImageButton ibClose;
    private TextView tvRoomName;
    private TextView tvRoomAdmin;
    private TextView tvRoomPwd;
    private Button btnShare;

    private String roomName;
    private String roomAdmin;
    private String roomPwd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shared);
        initView();
        initListener();
        setDatas();
    }

    private void initView() {
        ibClose = findViewById(R.id.ib_close);
        tvRoomName = findViewById(R.id.tv_room_name);
        tvRoomAdmin = findViewById(R.id.tv_room_admin);
        tvRoomPwd = findViewById(R.id.tv_room_password);
        btnShare = findViewById(R.id.btn_share);
    }

    private void initListener() {
        ibClose.setOnClickListener((v) -> finish());
        btnShare.setOnClickListener((v) -> shareRoom());
    }

    private void setDatas() {
        Intent gIntent = getIntent();
        if (gIntent == null) return;
        roomName = gIntent.getStringExtra(Constant.EXTRA_ROOM_NAME);
        roomAdmin = gIntent.getStringExtra(Constant.EXTRA_ROOM_ADMIN);
        roomPwd = gIntent.getStringExtra(Constant.EXTRA_ROOM_PWD);
        tvRoomName.setText("房间 " + getNoEmptyString(roomName));
        tvRoomAdmin.setText("房主：" + getNoEmptyString(roomAdmin));
        tvRoomPwd.setText("密码：" + getNoEmptyString(roomPwd));
    }

    private void shareRoom() {
        String shareContent = "房间：" + getNoEmptyString(roomName) + " \n" + "房主："
                + getNoEmptyString(roomAdmin) + " \n" + "密码：" + getNoEmptyString(roomPwd) + " \n" + "下载地址：" + Constant.DOWNLOAD_APPLINK;
        SystemShareUtils.shareTextAndPicToWechat(this, SystemShareUtils.NAME_ACTIVITY_WECHAT_FRIEND, shareContent, null);
        finish();
    }

    private String getNoEmptyString(String content) {
        if (TextUtils.isEmpty(content)) {
            return "空";
        } else {
            return content;
        }
    }

}
