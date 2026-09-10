package com.charging.c15station;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TokenManager tokenManager;
    private SwipeRefreshLayout swipeRefresh;
    private View bannerTokenWarning;
    private TextView tvTotalFree;
    private TextView tvStatusBadge;
    private TextView tvUpdateTime;
    private LinearLayout layoutPilesContainer;
    private ImageView btnSettings;
    private ImageView btnRefresh;

    private boolean isRefreshing = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tokenManager = new TokenManager(this);

        swipeRefresh = findViewById(R.id.swipe_refresh);
        bannerTokenWarning = findViewById(R.id.banner_token_warning);
        tvTotalFree = findViewById(R.id.tv_total_free);
        tvStatusBadge = findViewById(R.id.tv_status_badge);
        tvUpdateTime = findViewById(R.id.tv_update_time);
        layoutPilesContainer = findViewById(R.id.layout_piles_container);
        btnSettings = findViewById(R.id.btn_settings);
        btnRefresh = findViewById(R.id.btn_refresh);

        // 设置下拉刷新颜色
        swipeRefresh.setColorSchemeColors(Color.parseColor("#10B981"));
        swipeRefresh.setOnRefreshListener(this::fetchStatus);

        btnRefresh.setOnClickListener(v -> {
            if (!isRefreshing) {
                swipeRefresh.setRefreshing(true);
                fetchStatus();
            }
        });

        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        bannerTokenWarning.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        // 初始化预先填充 4 个电桩占位卡片
        renderEmptyPileCards();

        // 首次加载
        fetchStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 如果未设置 Token，提示去设置
        if (!tokenManager.hasToken()) {
            bannerTokenWarning.setVisibility(View.VISIBLE);
            tvStatusBadge.setText("未设置 Token");
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_busy);
        }
    }

    private void renderEmptyPileCards() {
        layoutPilesContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (PileInfo.PileConfig config : PileInfo.C15_PILES) {
            View card = inflater.inflate(R.layout.item_pile_card, layoutPilesContainer, false);
            TextView tvName = card.findViewById(R.id.tv_pile_name);
            TextView tvCode = card.findViewById(R.id.tv_pile_code);
            TextView tvBadge = card.findViewById(R.id.tv_pile_free_badge);
            GridLayout grid = card.findViewById(R.id.grid_ports);

            tvName.setText("华工大学城 " + config.name);
            tvCode.setText("设备编码: " + config.code);
            tvBadge.setText("加载中...");
            tvBadge.setBackgroundResource(R.drawable.bg_badge_busy);

            populatePortGrid(grid, null);
            layoutPilesContainer.addView(card);
        }
    }

    private void populatePortGrid(GridLayout grid, PileInfo.PileStatus status) {
        grid.removeAllViews();
        int total = 12;

        for (int i = 0; i < total; i++) {
            String portName = String.format(Locale.getDefault(), "%02d", i + 1);
            boolean isFree = false;

            if (status != null && status.success && i < status.ports.size()) {
                PileInfo.Port p = status.ports.get(i);
                portName = p.name;
                isFree = !p.isUse;
            }

            TextView portView = new TextView(this);
            portView.setGravity(Gravity.CENTER);
            portView.setTextSize(12);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = (int) (38 * getResources().getDisplayMetrics().density);
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(6, 6, 6, 6);
            portView.setLayoutParams(params);

            if (status == null || !status.success) {
                portView.setText(portName);
                portView.setBackgroundResource(R.drawable.bg_badge_busy);
                portView.setTextColor(Color.parseColor("#9CA3AF"));
            } else if (isFree) {
                // 空闲可用：亮绿色
                portView.setText(portName + "\n空闲");
                portView.setBackgroundResource(R.drawable.bg_badge_free);
                portView.setTextColor(Color.WHITE);
                portView.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                // 占用：浅灰底灰字
                portView.setText(portName + "\n占用");
                portView.setBackgroundResource(R.drawable.bg_badge_busy);
                portView.setTextColor(Color.parseColor("#6B7280"));
            }

            grid.addView(portView);
        }
    }

    private void fetchStatus() {
        final String token = tokenManager.getToken();
        if (token == null || token.trim().isEmpty()) {
            swipeRefresh.setRefreshing(false);
            bannerTokenWarning.setVisibility(View.VISIBLE);
            tvTotalFree.setText("0");
            tvStatusBadge.setText("未设置 Token");
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_busy);
            Toast.makeText(this, "请先在右上角【设置】中粘贴 Token！", Toast.LENGTH_SHORT).show();
            return;
        }

        isRefreshing = true;
        swipeRefresh.setRefreshing(true);
        tvStatusBadge.setText("正在刷新...");

        new Thread(() -> {
            final PileInfo.StationStatus stationStatus = ChargeClient.queryAllC15Piles(token);
            runOnUiThread(() -> {
                isRefreshing = false;
                swipeRefresh.setRefreshing(false);
                updateUI(stationStatus);
            });
        }).start();
    }

    private void updateUI(PileInfo.StationStatus status) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);
        tvUpdateTime.setText("更新时间: " + sdf.format(new Date(status.timestamp)));

        if (!status.tokenValid) {
            bannerTokenWarning.setVisibility(View.VISIBLE);
            tvStatusBadge.setText("Token 已失效");
            tvStatusBadge.setBackgroundResource(R.drawable.bg_banner_warn);
            Toast.makeText(this, "Token 已失效，请从电脑提取最新 Token 后更新！", Toast.LENGTH_LONG).show();
            return;
        }

        bannerTokenWarning.setVisibility(View.GONE);
        int totalFree = status.getTotalFreePorts();
        int totalPorts = status.getTotalPorts();
        tvTotalFree.setText(String.valueOf(totalFree));

        if (totalFree > 0) {
            tvStatusBadge.setText("有 " + totalFree + " 个空闲口可用");
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_free);
        } else {
            tvStatusBadge.setText("全满 (暂无空闲)");
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_busy);
        }

        // 更新 4 张电桩卡片
        layoutPilesContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < status.pileStatuses.size(); i++) {
            PileInfo.PileStatus ps = status.pileStatuses.get(i);
            View card = inflater.inflate(R.layout.item_pile_card, layoutPilesContainer, false);
            TextView tvName = card.findViewById(R.id.tv_pile_name);
            TextView tvCode = card.findViewById(R.id.tv_pile_code);
            TextView tvBadge = card.findViewById(R.id.tv_pile_free_badge);
            GridLayout grid = card.findViewById(R.id.grid_ports);

            tvName.setText("华工大学城 " + ps.name);
            tvCode.setText("设备编码: " + ps.code);

            if (ps.success) {
                int free = ps.getFreeCount();
                tvBadge.setText("空闲 " + free + " / " + ps.getTotalCount());
                if (free > 0) {
                    tvBadge.setBackgroundResource(R.drawable.bg_badge_free);
                } else {
                    tvBadge.setBackgroundResource(R.drawable.bg_badge_busy);
                }
            } else {
                tvBadge.setText("获取失败");
                tvBadge.setBackgroundResource(R.drawable.bg_badge_busy);
            }

            populatePortGrid(grid, ps);
            layoutPilesContainer.addView(card);
        }
    }
}
