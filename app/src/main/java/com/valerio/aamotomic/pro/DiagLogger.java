package com.valerio.aamotomic.pro;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bounded, rotated diagnostic logger with:
 * 1. Automatic size capping (max 600 lines / ~96 KB)
 * 2. Real-time Android Logcat stream (TAG: AAMotoMicPro)
 * 3. Embedded local Wi-Fi HTTP Server on port 8765 (GET /log, GET /status, GET /clear)
 * 4. Real-time UDP Log Broadcaster on port 59123
 */
public final class DiagLogger {
    private static final String TAG = "AAMotoMicPro";
    private static final String LOG_FILE_NAME = "diag_log_pro.txt";
    private static final int MAX_LINES = 600;
    public static final int HTTP_PORT = 8765;
    public static final int UDP_PORT = 59123;
    private static final Object LOCK = new Object();

    private static final Pattern MAC_PATTERN =
            Pattern.compile("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})");
    private static final Pattern NAME_PATTERN =
            Pattern.compile("name=([^,\\|\\]\\n]+)");

    private static volatile boolean serverStarted = false;
    private static ServerSocket serverSocket;
    private static DatagramSocket udpSocket;

    private DiagLogger() {}

    /**
     * Starts the embedded Wi-Fi HTTP log server & UDP broadcaster.
     */
    public static synchronized void ensureServerStarted(Context ctx) {
        if (serverStarted) return;
        serverStarted = true;

        Context appCtx = ctx.getApplicationContext();

        // Start UDP Socket
        try {
            udpSocket = new DatagramSocket();
            udpSocket.setBroadcast(true);
        } catch (Exception e) {
            Log.w(TAG, "UDP broadcast socket warning: " + e.getMessage());
        }

        // Start HTTP Server daemon thread on port 8765
        Thread serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(HTTP_PORT);
                Log.i(TAG, "LiveLogServer listening on http://0.0.0.0:" + HTTP_PORT);
                while (!Thread.currentThread().isInterrupted()) {
                    Socket client = serverSocket.accept();
                    handleClient(appCtx, client);
                }
            } catch (Exception e) {
                Log.w(TAG, "LiveLogServer stopped: " + e.getMessage());
            }
        }, "AAMotoMicPro-LiveLogServer");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private static void handleClient(Context ctx, Socket client) {
        new Thread(() -> {
            try {
                client.setSoTimeout(3000);
                InputStream in = client.getInputStream();
                OutputStream out = client.getOutputStream();

                // Read request line
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String requestLine = reader.readLine();
                if (requestLine == null) {
                    client.close();
                    return;
                }

                String[] parts = requestLine.split(" ");
                String path = (parts.length > 1) ? parts[1] : "/";

                String headerLine;
                while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                    // consume remaining HTTP request headers
                }

                byte[] responseBytes;
                String contentType = "text/plain; charset=utf-8";

                if (path.startsWith("/log")) {
                    String logs = readAll(ctx);
                    responseBytes = logs.getBytes(StandardCharsets.UTF_8);
                } else if (path.startsWith("/clear")) {
                    clear(ctx);
                    responseBytes = "LOGS CLEARED\n".getBytes(StandardCharsets.UTF_8);
                } else if (path.startsWith("/status")) {
                    contentType = "application/json; charset=utf-8";
                    String json = "{\n" +
                            "  \"running\": " + AudioRouterService.isRunning + ",\n" +
                            "  \"sessionOpen\": " + AudioRouterService.isSessionOpen + ",\n" +
                            "  \"scoForced\": " + AudioRouterService.isScoForced + ",\n" +
                            "  \"activeDevice\": \"" + AudioRouterService.activeDeviceSummary.replace("\"", "\\\"") + "\"\n" +
                            "}\n";
                    responseBytes = json.getBytes(StandardCharsets.UTF_8);
                } else {
                    // Quick HTML UI
                    contentType = "text/html; charset=utf-8";
                    String html = "<!DOCTYPE html><html><head><meta charset='utf-8'>" +
                            "<title>AAMotoMic Pro Live Logs</title>" +
                            "<meta http-equiv='refresh' content='3'>" +
                            "<style>body{background:#0D1117;color:#C9D1D9;font-family:monospace;padding:16px;}" +
                            "h2{color:#FF4B4B;}pre{background:#161B22;padding:12px;border-radius:8px;white-space:pre-wrap;}" +
                            "a{color:#58A6FF;}</style></head><body>" +
                            "<h2>AAMotoMic Pro - POCO X7 Pro Live Diag</h2>" +
                            "<p><b>Stato:</b> " + (AudioRouterService.isRunning ? "<span style='color:#3FB950'>ATTIVO</span>" : "<span style='color:#F85149'>FERMO</span>") +
                            " | <b>Device:</b> " + AudioRouterService.activeDeviceSummary + "</p>" +
                            "<p><a href='/log'>Testo Grezzo (/log)</a> | <a href='/status'>JSON Status (/status)</a> | <a href='/clear'>Pulisci (/clear)</a></p>" +
                            "<pre>" + escapeHtml(readAll(ctx)) + "</pre></body></html>";
                    responseBytes = html.getBytes(StandardCharsets.UTF_8);
                }

                String header = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: " + contentType + "\r\n" +
                        "Content-Length: " + responseBytes.length + "\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Connection: close\r\n\r\n";

                out.write(header.getBytes(StandardCharsets.UTF_8));
                out.write(responseBytes);
                out.flush();
                client.close();
            } catch (Exception ignored) {
                try {
                    client.close();
                } catch (Exception ignored2) {
                }
            }
        }).start();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static void log(Context ctx, String message) {
        Log.i(TAG, message);

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        String line = "[" + timestamp + "] " + message;

        // Broadcast over UDP in background thread so caller thread is NEVER blocked
        if (udpSocket != null) {
            new Thread(() -> {
                try {
                    byte[] data = line.getBytes(StandardCharsets.UTF_8);
                    DatagramPacket packet = new DatagramPacket(
                            data, data.length,
                            InetAddress.getByName("255.255.255.255"), UDP_PORT);
                    udpSocket.send(packet);
                } catch (Exception ignored) {
                }
            }).start();
        }

        if (ctx == null || !AppPrefs.isDiagLoggingEnabled(ctx)) {
            return;
        }

        ensureServerStarted(ctx);

        synchronized (LOCK) {
            try {
                File file = new File(ctx.getFilesDir(), LOG_FILE_NAME);
                List<String> lines = readLinesInternal(file);
                lines.add(line);
                if (lines.size() > MAX_LINES) {
                    lines = lines.subList(lines.size() - MAX_LINES, lines.size());
                }
                StringBuilder sb = new StringBuilder(lines.size() * 64);
                for (String l : lines) {
                    sb.append(l).append('\n');
                }
                try (FileOutputStream fos = new FileOutputStream(file, false)) {
                    fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed writing diagnostic log", e);
            }
        }
    }

    public static String readAll(Context ctx) {
        synchronized (LOCK) {
            File file = new File(ctx.getFilesDir(), LOG_FILE_NAME);
            if (!file.exists() || file.length() == 0) {
                return "Nessun log diagnostico presente.";
            }
            List<String> lines = readLinesInternal(file);
            StringBuilder sb = new StringBuilder();
            for (String l : lines) {
                sb.append(l).append('\n');
            }
            return sb.toString().trim();
        }
    }

    public static long getSizeKb(Context ctx) {
        synchronized (LOCK) {
            File file = new File(ctx.getFilesDir(), LOG_FILE_NAME);
            if (!file.exists()) return 0L;
            return Math.max(1L, Math.round(file.length() / 1024.0));
        }
    }

    public static void clear(Context ctx) {
        synchronized (LOCK) {
            File file = new File(ctx.getFilesDir(), LOG_FILE_NAME);
            if (file.exists()) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    public static String sanitize(String raw) {
        if (raw == null) return "";
        return MAC_PATTERN.matcher(raw).replaceAll("XX:XX:XX:XX:XX:XX");
    }

    public static List<String> extractRecentDevices(Context ctx) {
        List<String> result = new ArrayList<>();
        Map<String, String> macToName = new LinkedHashMap<>();

        synchronized (LOCK) {
            File file = new File(ctx.getFilesDir(), LOG_FILE_NAME);
            if (!file.exists()) return result;

            List<String> lines = readLinesInternal(file);
            for (String line : lines) {
                if (!line.contains("[DEVICES]") && !line.contains("[SCO]")) continue;

                Matcher macMatcher = MAC_PATTERN.matcher(line);
                Matcher nameMatcher = NAME_PATTERN.matcher(line);

                String mac = null;
                if (macMatcher.find()) {
                    mac = macMatcher.group(0).toUpperCase(Locale.US);
                }

                String name = "";
                if (nameMatcher.find()) {
                    name = nameMatcher.group(1).trim();
                }

                if (mac != null && !mac.isEmpty() && !macToName.containsKey(mac)) {
                    macToName.put(mac, name.isEmpty() ? "Dispositivo Bluetooth" : name);
                }
            }
        }

        for (Map.Entry<String, String> e : macToName.entrySet()) {
            result.add(e.getKey() + " (" + e.getValue() + ")");
        }
        return result;
    }

    private static List<String> readLinesInternal(File file) {
        List<String> lines = new ArrayList<>();
        if (!file.exists()) return lines;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        } catch (Exception ignored) {
        }
        return lines;
    }
}
