package Server.Middleware;

import Server.Common.Trace;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Vector;

public class TCPResourceManagerClient {

    private final String host;
    private final int port;
    private final String tag; // for logging

    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;

    public TCPResourceManagerClient(String host, int port, String tag) throws IOException {
        this.host = host;
        this.port = port;
        this.tag  = tag == null ? "" : tag;
        connect(true);
    }

    public synchronized boolean sendBool(String method, Object... args) throws IOException {
        String s = send(method, args);
        ensureOk(s);
        return s.replaceAll("\\s+", "").contains("\"response\":true");
    }

    public synchronized int sendInt(String method, Object... args) throws IOException {
        String s = send(method, args);
        ensureOk(s);
        String flat = s.replaceAll("\\s+", "");
        int idx = flat.indexOf("\"response\":");
        if (idx < 0) throw new IOException("Malformed response from " + tag);
        int p = idx + 11;
        StringBuilder num = new StringBuilder();
        while (p < flat.length()) {
            char c = flat.charAt(p);
            if (c == '-' || Character.isDigit(c)) { num.append(c); p++; }
            else break;
        }
        try {
            return Integer.parseInt(num.toString());
        } catch (Exception e) {
            throw new IOException("Expected integer response from " + tag);
        }
    }

    public synchronized String sendString(String method, Object... args) throws IOException {
        String s = send(method, args);
        ensureOk(s);
        String flat = s.replaceAll("\\s+", "");
        if (flat.contains("\"response\":null")) return "";
        int idx = flat.indexOf("\"response\":\"");
        if (idx < 0) throw new IOException("Expected string response from " + tag);
        int p = idx + 12;
        StringBuilder out = new StringBuilder();
        boolean esc = false;
        while (p < flat.length()) {
            char c = flat.charAt(p++);
            if (esc) { out.append(c); esc = false; continue; }
            if (c == '\\') { esc = true; continue; }
            if (c == '"') break;
            out.append(c);
        }
        return out.toString();
    }

    private String send(String method, Object... args) throws IOException {
        String payload = buildJson(method, args);

        try {
            out.write(payload);
            out.write("\n");
            out.flush();

            String line = in.readLine();
            if (line == null) throw new IOException("Connection closed by RM: " + tag);
            return line;

        } catch (IOException e) {
            Trace.warn("[" + tag + "] connection issue: " + e.getMessage() + " -> reconnecting");
            reconnect();
            out.write(payload);
            out.write("\n");
            out.flush();
            String line = in.readLine();
            if (line == null) throw new IOException("Connection closed by RM after reconnect: " + tag);
            return line;
        }
    }

    private String buildJson(String method, Object... args) {
        StringBuilder b = new StringBuilder();
        b.append("{\"method\":\"").append(esc(method)).append("\",\"args\":[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) b.append(",");
            Object v = args[i];
            if (v == null) { b.append("null"); continue; }
            if (v instanceof Number || v instanceof Boolean) {
                b.append(v.toString());
            } else if (v instanceof Vector) {
                @SuppressWarnings("unchecked")
                Vector<String> vec = (Vector<String>) v;
                b.append("[");
                for (int j = 0; j < vec.size(); j++) {
                    if (j > 0) b.append(",");
                    b.append("\"").append(esc(vec.get(j))).append("\"");
                }
                b.append("]");
            } else if (v.getClass().isArray()) {
                Object[] arr = (Object[]) v;
                b.append("[");
                for (int j = 0; j < arr.length; j++) {
                    if (j > 0) b.append(",");
                    Object e = arr[j];
                    if (e instanceof Number || e instanceof Boolean) b.append(e.toString());
                    else b.append("\"").append(esc(String.valueOf(e))).append("\"");
                }
                b.append("]");
            } else {
                b.append("\"").append(esc(String.valueOf(v))).append("\"");
            }
        }
        b.append("]}");
        return b.toString();
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void ensureOk(String resp) throws IOException {
        String flat = resp.replaceAll("\\s+", "");
        if (flat.contains("\"status\":\"failed\"")) {
            String msg = fieldString(flat, "message");
            throw new IOException(msg == null ? "Operation failed" : msg);
        }
    }

    private static String fieldString(String s, String field) {
        String needle = "\"" + field + "\":\"";
        int i = s.indexOf(needle);
        if (i < 0) return null;
        int p = i + needle.length();
        StringBuilder out = new StringBuilder();
        boolean esc = false;
        while (p < s.length()) {
            char c = s.charAt(p++);
            if (esc) { out.append(c); esc = false; continue; }
            if (c == '\\') { esc = true; continue; }
            if (c == '"') break;
            out.append(c);
        }
        return out.toString();
    }

    private void connect(boolean firstLog) throws IOException {
        boolean first = true;
        while (true) {
            try {
                socket = new Socket(host, port);
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);
                in  = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                if (firstLog) System.out.println("Connected to " + tag + " RM [" + host + ":" + port + "]");
                return;
            } catch (IOException e) {
                if (first) {
                    System.out.println("Waiting for " + tag + " RM [" + host + ":" + port + "]");
                    first = false;
                }
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
        }
    }

    private void reconnect() throws IOException {
        close();
        connect(false);
    }

    public void close() throws IOException {
        try { if (in != null) in.close(); } catch (Exception ignored) {}
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (Exception ignored) {}
    }
}
