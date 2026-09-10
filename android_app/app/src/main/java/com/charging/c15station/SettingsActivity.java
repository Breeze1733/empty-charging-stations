package com.charging.c15station;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class SettingsActivity extends AppCompatActivity {

    private TextInputEditText etToken;
    private MaterialButton btnPaste;
    private MaterialButton btnClear;
    private MaterialButton btnSaveTest;
    private TextView tvVerifyStatus;
    private TokenManager tokenManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        tokenManager = new TokenManager(this);

        Toolbar toolbar = findViewById(R.id.toolbar_settings);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etToken = findViewById(R.id.et_token);
        btnPaste = findViewById(R.id.btn_paste);
        btnClear = findViewById(R.id.btn_clear);
        btnSaveTest = findViewById(R.id.btn_save_test);
        tvVerifyStatus = findViewById(R.id.tv_verify_status);

        // 加载当前保存的 Token
        String currentToken = tokenManager.getToken();
        if (currentToken != null && !currentToken.isEmpty()) {
            etToken.setText(currentToken);
            tvVerifyStatus.setText("已保存 Token (点击下方按钮可重新测试)");
            tvVerifyStatus.setTextColor(Color.parseColor("#4B5563"));
        } else {
            tvVerifyStatus.setText("未设置 Token，请粘贴最新 Token");
            tvVerifyStatus.setTextColor(Color.parseColor("#EF4444"));
        }

        // 粘贴按钮
        btnPaste.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = clipboard.getPrimaryClip().getItemAt(0).getText();
                if (text != null && text.length() > 0) {
                    etToken.setText(text.toString().trim());
                    Toast.makeText(SettingsActivity.this, "已粘贴剪贴板内容", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(SettingsActivity.this, "剪贴板为空", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(SettingsActivity.this, "无法读取剪贴板", Toast.LENGTH_SHORT).show();
            }
        });

        // 清空按钮
        btnClear.setOnClickListener(v -> etToken.setText(""));

        // 测试并保存
        btnSaveTest.setOnClickListener(v -> doTestAndSave());
    }

    private void doTestAndSave() {
        final String inputToken = etToken.getText() != null ? etToken.getText().toString().trim() : "";
        if (inputToken.isEmpty()) {
            Toast.makeText(this, "请输入或粘贴 Token！", Toast.LENGTH_SHORT).show();
            tvVerifyStatus.setText("❌ Token 不能为空");
            tvVerifyStatus.setTextColor(Color.parseColor("#EF4444"));
            return;
        }

        btnSaveTest.setEnabled(false);
        tvVerifyStatus.setText("⏳ 正在连接服务器验证 Token...");
        tvVerifyStatus.setTextColor(Color.parseColor("#3B82F6"));

        new Thread(() -> {
            boolean valid = ChargeClient.verifyToken(inputToken);
            runOnUiThread(() -> {
                btnSaveTest.setEnabled(true);
                if (valid) {
                    tokenManager.saveTokenOnly(inputToken);
                    tvVerifyStatus.setText("✅ Token 验证成功！已永久保存到本地");
                    tvVerifyStatus.setTextColor(Color.parseColor("#10B981"));
                    Toast.makeText(SettingsActivity.this, "Token 验证成功并已保存！", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                } else {
                    tvVerifyStatus.setText("❌ Token 验证失败：已失效或网络连接超时");
                    tvVerifyStatus.setTextColor(Color.parseColor("#EF4444"));
                    Toast.makeText(SettingsActivity.this, "Token 校验未通过，请确认是否过期！", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }
}
