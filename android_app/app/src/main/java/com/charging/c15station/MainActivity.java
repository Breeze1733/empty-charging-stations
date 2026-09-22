package com.charging.c15station;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
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

    private SwipeRefreshLayout swipeRefresh;
    private View bannerTokenWarning;
    private TextView tvTotalFree;
    private TextView tvStatusBadge;
    private TextView tvUpdateTime;
    private ImageView btnRefresh;
    private TextView btnSwitchView;

    private View layoutMapContainer;
    private LinearLayout layoutPilesContainer;
    private LinearLayout layoutMapNorthLeft;
    private LinearLayout layoutMapNorthRight;
    private LinearLayout layoutMapSouthLeft;
    private LinearLayout layoutMapSouthRight;

    private TokenManager tokenManager;
    
    private String currentViewMode = "map";
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
        btnRefresh = findViewById(R.id.btn_refresh);
        btnSwitchView = findViewById(R.id.btn_switch_view);

        layoutMapContainer = findViewById(R.id.layout_map_container);
        layoutPilesContainer = findViewById(R.id.layout_piles_container);
        layoutMapNorthLeft = findViewById(R.id.layout_map_north_left);
        layoutMapNorthRight = findViewById(R.id.layout_map_north_right);
        layoutMapSouthLeft = findViewById(R.id.layout_map_south_left);
        layoutMapSouthRight = findViewById(R.id.layout_map_south_right);

        // 设置下拉刷新颜色
        swipeRefresh.setColorSchemeColors(Color.parseColor("#10B981"));
        swipeRefresh.setOnRefreshListener(this::fetchStatus);

        // 顶栏刷新按钮
        btnRefresh.setOnClickListener(v -> {
            if (!isRefreshing) {
                swipeRefresh.setRefreshing(true);
                fetchStatus();
            }
        });

        // 顶栏视图切换文字按钮
        btnSwitchView.setOnClickListener(v -> {
            if ("map".equalsIgnoreCase(currentViewMode)) {
                setViewMode("list", true);
            } else {
                setViewMode("map", true);
            }
        });

        // 初始化预先填充两个视图的占位状态
        renderEmptyPileCards();
        renderEmptyMapRows();

        // 恢复上次用户选择的视图模式 (默认地图视图)
        String savedMode = tokenManager.getViewMode();
        setViewMode(savedMode != null ? savedMode : "map", false);

        // 首次加载
        fetchStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!tokenManager.hasToken()) {
            bannerTokenWarning.setVisibility(View.VISIBLE);
            tvStatusBadge.setText("未设置 Token");
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_busy);
        } else {
            bannerTokenWarning.setVisibility(View.GONE);
        }
    }

    private int dpToPx(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void setViewMode(String mode, boolean notifyUser) {
        this.currentViewMode = mode;
        tokenManager.setViewMode(mode);

        boolean isMap = "map".equalsIgnoreCase(mode);
        if (isMap) {
            layoutMapContainer.setVisibility(View.VISIBLE);
            layoutPilesContainer.setVisibility(View.GONE);
            btnSwitchView.setText("切换视图");
            if (notifyUser) {
                Toast.makeText(this, "已切换为走廊地图视图", Toast.LENGTH_SHORT).show();
            }
        } else {
            layoutMapContainer.setVisibility(View.GONE);
            layoutPilesContainer.setVisibility(View.VISIBLE);
            btnSwitchView.setText("切换视图");
            if (notifyUser) {
                Toast.makeText(this, "已切换为电桩列表视图", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private PileInfo.PileStatus findPile(PileInfo.StationStatus status, int configIndex) {
        if (status == null || status.pileStatuses == null || configIndex >= PileInfo.C15_PILES.length) {
            return null;
        }
        String targetCode = PileInfo.C15_PILES[configIndex].code;
        for (PileInfo.PileStatus ps : status.pileStatuses) {
            if (ps != null && targetCode.equals(ps.code)) {
                return ps;
            }
        }
        if (configIndex < status.pileStatuses.size()) {
            return status.pileStatuses.get(configIndex);
        }
        return null;
    }

    private PileInfo.Port findPort(PileInfo.PileStatus pile, int portNum) {
        if (pile == null || pile.ports == null) return null;
        String p2 = String.format(Locale.getDefault(), "%02d", portNum);
        String p1 = String.valueOf(portNum);
        for (PileInfo.Port p : pile.ports) {
            if (p.name != null && (p.name.equals(p2) || p.name.equals(p1) || p.name.endsWith(p2))) {
                return p;
            }
        }
        if (portNum >= 1 && portNum <= pile.ports.size()) {
            return pile.ports.get(portNum - 1);
        }
        return null;
    }

    // ==================== 真实空间走廊地图视图逻辑 ====================

    private void renderEmptyMapRows() {
        updateMapRows(null);
    }

    private void updateMapRows(PileInfo.StationStatus status) {
        layoutMapNorthLeft.removeAllViews();
        layoutMapNorthRight.removeAllViews();
        layoutMapSouthLeft.removeAllViews();
        layoutMapSouthRight.removeAllViews();

        PileInfo.PileStatus pile1 = findPile(status, 0); // 1号充电桩 (左侧偏下)
        PileInfo.PileStatus pile2 = findPile(status, 1); // 2号充电桩 (左侧偏上)
        PileInfo.PileStatus pile3 = findPile(status, 2); // 3号充电桩 (右侧偏上)
        PileInfo.PileStatus pile4 = findPile(status, 3); // 4号充电桩 (右侧偏下)

        boolean p1Ok = (pile1 != null && pile1.success);
        boolean p2Ok = (pile2 != null && pile2.success);
        boolean p3Ok = (pile3 != null && pile3.success);
        boolean p4Ok = (pile4 != null && pile4.success);

        // ======================= 北区左列 (2号桩 12~01, 1号桩 12~07) =======================
        // 2-12 到 2-04 (9个插口)
        for (int p = 12; p >= 4; p--) {
            String label = String.format(Locale.getDefault(), "2-%02d", p);
            layoutMapNorthLeft.addView(createMapPortView(label, findPort(pile2, p), p2Ok));
        }
        // 隔板 (通道不隔断)
        layoutMapNorthLeft.addView(createSidePartitionView("隔板"));
        // 2-03 到 2-01 (3个插口)
        for (int p = 3; p >= 1; p--) {
            String label = String.format(Locale.getDefault(), "2-%02d", p);
            layoutMapNorthLeft.addView(createMapPortView(label, findPort(pile2, p), p2Ok));
        }
        // 1-12 到 1-07 (6个插口)
        for (int p = 12; p >= 7; p--) {
            String label = String.format(Locale.getDefault(), "1-%02d", p);
            layoutMapNorthLeft.addView(createMapPortView(label, findPort(pile1, p), p1Ok));
        }

        // ======================= 北区右列 (3号桩 01~12, 4号桩 01~06) =======================
        // 3-01 到 3-09 (9个插口)
        for (int p = 1; p <= 9; p++) {
            String label = String.format(Locale.getDefault(), "3-%02d", p);
            layoutMapNorthRight.addView(createMapPortView(label, findPort(pile3, p), p3Ok));
        }
        // 隔板 (通道不隔断)
        layoutMapNorthRight.addView(createSidePartitionView("隔板"));
        // 3-10 到 3-12 (3个插口)
        for (int p = 10; p <= 12; p++) {
            String label = String.format(Locale.getDefault(), "3-%02d", p);
            layoutMapNorthRight.addView(createMapPortView(label, findPort(pile3, p), p3Ok));
        }
        // 4-01 到 4-06 (6个插口)
        for (int p = 1; p <= 6; p++) {
            String label = String.format(Locale.getDefault(), "4-%02d", p);
            layoutMapNorthRight.addView(createMapPortView(label, findPort(pile4, p), p4Ok));
        }

        // ======================= 南区左列 (1号桩 06~01，阻断隔板位于1-06上方) =======================
        // 1-06 到 1-01 (6个插口)
        for (int p = 6; p >= 1; p--) {
            String label = String.format(Locale.getDefault(), "1-%02d", p);
            layoutMapSouthLeft.addView(createMapPortView(label, findPort(pile1, p), p1Ok));
        }

        // ======================= 南区右列 (4号桩 07~12，阻断隔板位于4-07上方) =======================
        // 4-07 到 4-12 (6个插口)
        for (int p = 7; p <= 12; p++) {
            String label = String.format(Locale.getDefault(), "4-%02d", p);
            layoutMapSouthRight.addView(createMapPortView(label, findPort(pile4, p), p4Ok));
        }
    }

    private View createSidePartitionView(String text) {
        TextView tv = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(12)
        );
        params.setMargins(dpToPx(0.5f), dpToPx(0.5f), dpToPx(0.5f), dpToPx(0.5f));
        tv.setLayoutParams(params);
        tv.setGravity(Gravity.CENTER);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 7.5f);
        tv.setTextColor(Color.parseColor("#94A3B8"));
        tv.setTypeface(null, Typeface.BOLD);
        tv.setBackgroundResource(R.drawable.bg_partition_side);
        tv.setIncludeFontPadding(false);
        return tv;
    }

    private TextView createMapPortView(String portLabel, PileInfo.Port port, boolean pileSuccess) {
        TextView tv = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(25)
        );
        params.setMargins(dpToPx(0.5f), dpToPx(0.5f), dpToPx(0.5f), dpToPx(0.5f));
        tv.setLayoutParams(params);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f);
        tv.setIncludeFontPadding(false);
        tv.setLineSpacing(0, 0.85f);

        if (!pileSuccess || port == null) {
            tv.setText(portLabel + "\n--");
            tv.setBackgroundResource(R.drawable.bg_port_compact_busy);
            tv.setTextColor(Color.parseColor("#9CA3AF"));
        } else if (!port.isUse) {
            // 空闲可用：亮绿底白字 (正方形示意，换行展示状态)
            tv.setText(portLabel + "\n空闲");
            tv.setBackgroundResource(R.drawable.bg_port_compact_free);
            tv.setTextColor(Color.WHITE);
            tv.setTypeface(null, Typeface.BOLD);
        } else {
            // 已占用：浅灰底灰字
            tv.setText(portLabel + "\n占用");
            tv.setBackgroundResource(R.drawable.bg_port_compact_busy);
            tv.setTextColor(Color.parseColor("#6B7280"));
        }
        return tv;
    }

    // ==================== 列表视图逻辑 ====================

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

    private void updateListCards(PileInfo.StationStatus status) {
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

    private void populatePortGrid(GridLayout grid, PileInfo.PileStatus status) {
        grid.removeAllViews();
        int total = 12;

        for (int p = 1; p <= total; p++) {
            String portName = String.format(Locale.getDefault(), "%02d", p);
            TextView portView = new TextView(this);
            portView.setGravity(Gravity.CENTER);
            portView.setTextSize(12);

            PileInfo.Port port = null;
            if (status != null && status.ports != null) {
                for (PileInfo.Port item : status.ports) {
                    if (item.name != null && (item.name.equals(portName) || item.name.endsWith(portName))) {
                        port = item;
                        break;
                    }
                }
            }

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dpToPx(38);
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(6, 6, 6, 6);
            portView.setLayoutParams(params);

            if (status == null || !status.success || port == null) {
                portView.setText(portName);
                portView.setBackgroundResource(R.drawable.bg_badge_busy);
                portView.setTextColor(Color.parseColor("#9CA3AF"));
            } else if (!port.isUse) {
                // 空闲：绿底白字
                portView.setText(portName + "\n空闲");
                portView.setBackgroundResource(R.drawable.bg_badge_free);
                portView.setTextColor(Color.WHITE);
                portView.setTypeface(null, Typeface.BOLD);
            } else {
                // 占用：浅灰底灰字
                portView.setText(portName + "\n占用");
                portView.setBackgroundResource(R.drawable.bg_badge_busy);
                portView.setTextColor(Color.parseColor("#6B7280"));
            }

            grid.addView(portView);
        }
    }

    // ==================== 网络刷新与数据加载 ====================

    private void fetchStatus() {
        final String token = tokenManager.getToken();
        if (token == null || token.trim().isEmpty()) {
            swipeRefresh.setRefreshing(false);
            bannerTokenWarning.setVisibility(View.VISIBLE);
            tvStatusBadge.setText("未设置 Token");
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_busy);
            return;
        }

        isRefreshing = true;
        tvStatusBadge.setText("刷新中...");
        tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_busy);

        new Thread(() -> {
            PileInfo.StationStatus stationStatus = ChargeClient.queryAllC15Piles(token);
            runOnUiThread(() -> {
                isRefreshing = false;
                swipeRefresh.setRefreshing(false);
                updateUI(stationStatus);
            });
        }).start();
    }

    private void updateUI(PileInfo.StationStatus status) {
        String now = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        tvUpdateTime.setText("更新时间: " + now);

        if (status == null) {
            tvStatusBadge.setText("网络异常");
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_busy);
            return;
        }

        if (status.tokenValid) {
            bannerTokenWarning.setVisibility(View.GONE);
            int free = status.getTotalFreePorts();
            tvTotalFree.setText(String.valueOf(free));
            tvStatusBadge.setText("正常");
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_free);
        } else {
            bannerTokenWarning.setVisibility(View.VISIBLE);
            tvStatusBadge.setText("Token失效");
            tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_busy);
        }

        // 同步更新两个视图
        updateMapRows(status);
        updateListCards(status);
    }
}
