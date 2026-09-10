package com.charging.c15station;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class ChargeClient {
    private static final String BASE_URL = "https://hgcms.gzyzinfo.com:442/ChargeBoxService/";
    private static final byte[] DES_KEY = new byte[]{'y', 'z', '_', 'c', 'b', 'o', 'x', 0};

    static {
        // 配置信任所有证书，防止校园网/局域网中间证书截断导致 SSL 失败
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception ignored) {
        }
    }

    /**
     * 对 JSON 字符串进行 DES-ECB PKCS5Padding 加密，并转为十六进制字符串
     */
    public static String encryptDES(String plainText) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(DES_KEY, "DES");
        Cipher cipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : encryptedBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * 发送 POST 请求
     */
    private static String doPost(String endpoint, String paraJson, String token) throws Exception {
        URL url = new URL(BASE_URL + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        String mobileTime = sdf.format(new Date());

        String paraHex = encryptDES(paraJson);
        StringBuilder body = new StringBuilder();
        body.append("para=").append(URLEncoder.encode(paraHex, "UTF-8"));
        body.append("&mobileTime=").append(URLEncoder.encode(mobileTime, "UTF-8"));
        if (token != null && !token.trim().isEmpty()) {
            body.append("&token=").append(URLEncoder.encode(token.trim(), "UTF-8"));
        }

        byte[] postData = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(postData);
            os.flush();
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) {
            throw new RuntimeException("HTTP 响应码异常: " + code);
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }

    /**
     * 验证 Token 是否有效
     */
    public static boolean verifyToken(String token) {
        if (token == null || token.trim().isEmpty()) return false;
        try {
            String resp = doPost("mobile/chargeLocker/stationList.do", "{}", token);
            JSONObject obj = new JSONObject(resp);
            return "true".equalsIgnoreCase(obj.optString("state"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 查询指定桩的所有插口可用性
     */
    public static PileInfo.PileStatus queryPile(String pileCode, String pileName, String token) {
        try {
            JSONObject req = new JSONObject();
            req.put("code", pileCode);
            String resp = doPost("mobile/chargeLocker/chargeBoxList.do", req.toString(), token);
            JSONObject root = new JSONObject(resp);

            if (!"true".equalsIgnoreCase(root.optString("state"))) {
                String desc = root.optString("descriptions", "获取失败");
                return new PileInfo.PileStatus(pileName, pileCode, desc);
            }

            JSONObject content = root.optJSONObject("content");
            List<PileInfo.Port> ports = new ArrayList<>();
            if (content != null) {
                JSONArray list = content.optJSONArray("list");
                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject item = list.getJSONObject(i);
                        String name = item.optString("name", String.format(Locale.getDefault(), "%02d", i + 1));
                        boolean isUse = item.optBoolean("isUse", false);
                        ports.add(new PileInfo.Port(name, isUse));
                    }
                }
            }
            return new PileInfo.PileStatus(pileName, pileCode, ports);
        } catch (Exception e) {
            return new PileInfo.PileStatus(pileName, pileCode, "请求失败: " + e.getMessage());
        }
    }

    /**
     * 查询华工大学城 C15 全部 1~4 号充电桩的实时状态
     */
    public static PileInfo.StationStatus queryAllC15Piles(String token) {
        if (token == null || token.trim().isEmpty()) {
            return new PileInfo.StationStatus(null, false, "未配置 Token，请在设置中输入");
        }

        List<PileInfo.PileStatus> results = new ArrayList<>();
        boolean anyTokenFail = false;

        for (PileInfo.PileConfig config : PileInfo.C15_PILES) {
            PileInfo.PileStatus status = queryPile(config.code, config.name, token);
            results.add(status);
            if (!status.success && (status.errorMsg.contains("登录") || status.errorMsg.contains("超时") || status.errorMsg.contains("失效"))) {
                anyTokenFail = true;
            }
        }

        boolean valid = !anyTokenFail;
        String msg = valid ? "查询成功" : "Token 可能已失效，请更新 Token";
        return new PileInfo.StationStatus(results, valid, msg);
    }
}
