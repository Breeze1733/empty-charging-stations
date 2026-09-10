package com.charging.c15station;

import java.util.ArrayList;
import java.util.List;

public class PileInfo {
    public static final String STATION_NAME = "华工大学城 C15";
    public static final String STATION_CODE = "202311011459475935260";

    // 华工大学城 C15 四个充电桩编码
    public static final PileConfig[] C15_PILES = new PileConfig[]{
            new PileConfig("1号充电桩", "861714054442714", 12),
            new PileConfig("2号充电桩", "861714054100585", 12),
            new PileConfig("3号充电桩", "861714054436518", 12),
            new PileConfig("4号充电桩", "863488056590576", 12)
    };

    public static class PileConfig {
        public final String name;
        public final String code;
        public final int totalPorts;

        public PileConfig(String name, String code, int totalPorts) {
            this.name = name;
            this.code = code;
            this.totalPorts = totalPorts;
        }
    }

    public static class Port {
        public final String name;     // "01", "02", ..., "12"
        public final boolean isUse;   // true: 占用, false: 空闲可用

        public Port(String name, boolean isUse) {
            this.name = name;
            this.isUse = isUse;
        }
    }

    public static class PileStatus {
        public final String name;
        public final String code;
        public final List<Port> ports;
        public final boolean success;
        public final String errorMsg;

        public PileStatus(String name, String code, List<Port> ports) {
            this.name = name;
            this.code = code;
            this.ports = ports != null ? ports : new ArrayList<>();
            this.success = true;
            this.errorMsg = "";
        }

        public PileStatus(String name, String code, String errorMsg) {
            this.name = name;
            this.code = code;
            this.ports = new ArrayList<>();
            this.success = false;
            this.errorMsg = errorMsg;
        }

        public int getFreeCount() {
            int count = 0;
            for (Port p : ports) {
                if (!p.isUse) {
                    count++;
                }
            }
            return count;
        }

        public int getTotalCount() {
            return ports.size();
        }
    }

    public static class StationStatus {
        public final List<PileStatus> pileStatuses;
        public final boolean tokenValid;
        public final String message;
        public final long timestamp;

        public StationStatus(List<PileStatus> pileStatuses, boolean tokenValid, String message) {
            this.pileStatuses = pileStatuses != null ? pileStatuses : new ArrayList<>();
            this.tokenValid = tokenValid;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }

        public int getTotalFreePorts() {
            int total = 0;
            for (PileStatus p : pileStatuses) {
                if (p.success) {
                    total += p.getFreeCount();
                }
            }
            return total;
        }

        public int getTotalPorts() {
            int total = 0;
            for (PileStatus p : pileStatuses) {
                if (p.success) {
                    total += p.getTotalCount();
                }
            }
            return total;
        }
    }
}
