package Server.TCP;

import Server.Common.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class TCPResourceManager {

    private final ResourceManager rm;

    public TCPResourceManager(String name) {
        this.rm = new ResourceManager(name);
    }

    public static void main(String[] args) {
        String name = (args.length > 0) ? args[0] : "Server";
        int port = (args.length > 1) ? parseIntOr(args[1], 5001) : 5001;

        TCPResourceManager server = new TCPResourceManager(name);

        try (ServerSocket ss = new ServerSocket(port)) {
            System.out.println("[TCPResourceManager:" + name + "] listening on :" + port);
            while (true) {
                Socket s = ss.accept();
                s.setTcpNoDelay(true);
                new Thread(new ClientHandler(s, server.rm, name), "rm-" + name + "-" + s.getPort()).start();
            }
        } catch (IOException e) {
            System.err.println("[TCPResourceManager:" + name + "] fatal: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static int parseIntOr(String x, int dflt) {
        try { return Integer.parseInt(x); } catch (Exception e) { return dflt; }
    }

    private static final class ClientHandler implements Runnable {
        private final Socket socket;
        private final ResourceManager rm;
        private final String tag;

        ClientHandler(Socket socket, ResourceManager rm, String tag) {
            this.socket = socket;
            this.rm = rm;
            this.tag = tag;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = in.readLine()) != null) {
                    String resp;
                    try {
                        Request req = Request.parse(line);
                        Object r = dispatch(req);
                        resp = Json.success(r);
                    } catch (Exception ex) {
                        resp = Json.failed(ex.getMessage() == null ? "Operation failed" : ex.getMessage());
                    }
                    out.write(resp);
                    out.write("\n");
                    out.flush();
                }
            } catch (IOException ignored) {
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private Object dispatch(Request req) throws Exception {
            String m = req.method;
            Object[] a = req.args;

            switch (m) {
                case "getName":
                    return rm.getName();

                // flights
                case "addFlight":
                    return rm.addFlight(i(a,0), i(a,1), i(a,2));
                case "deleteFlight":
                    return rm.deleteFlight(i(a,0));
                case "queryFlight":
                    return rm.queryFlight(i(a,0));
                case "queryFlightPrice":
                    return rm.queryFlightPrice(i(a,0));
                case "reserveFlight":
                    return rm.reserveFlight(i(a,0), i(a,1)); // customerID is ignored by RM logic

                // cars
                case "addCars":
                    return rm.addCars(s(a,0), i(a,1), i(a,2));
                case "deleteCars":
                    return rm.deleteCars(s(a,0));
                case "queryCars":
                    return rm.queryCars(s(a,0));
                case "queryCarsPrice":
                    return rm.queryCarsPrice(s(a,0));
                case "reserveCar":
                    return rm.reserveCar(i(a,0), s(a,1)); // customerID ignored by RM

                // rooms
                case "addRooms":
                    return rm.addRooms(s(a,0), i(a,1), i(a,2));
                case "deleteRooms":
                    return rm.deleteRooms(s(a,0));
                case "queryRooms":
                    return rm.queryRooms(s(a,0));
                case "queryRoomsPrice":
                    return rm.queryRoomsPrice(s(a,0));
                case "reserveRoom":
                    return rm.reserveRoom(i(a,0), s(a,1)); // customerID ignored by RM
                case "removeReservation":
                    return rm.removeReservation(i(a,0), s(a,1), i(a,2));
                case "bundle":
                    throw new IllegalArgumentException("bundle not supported at RM");

                    // no customer endpoints here by design
                default:
                    throw new IllegalArgumentException("Unknown method: " + m);
            }
        }

        private static int i(Object[] a, int idx) { return ((Number)a[idx]).intValue(); }
        private static String s(Object[] a, int idx) { return String.valueOf(a[idx]); }
    }

    private static final class Request {
        final String method;
        final Object[] args;

        private Request(String m, Object[] a) { this.method = m; this.args = a; }

        static Request parse(String s) {
            String method = Json.getString(s, "method");
            Object[] args = Json.getArray(s, "args");
            if (method == null || args == null) throw new IllegalArgumentException("Malformed JSON");
            return new Request(method, args);
        }
    }

    private static final class Json {
        static String success(Object v) {
            return "{\"status\":\"ok\",\"response\":" + enc(v) + "}";
        }
        static String failed(String msg) {
            return "{\"status\":\"failed\",\"message\":\"" + esc(msg) + "\"}";
        }

        static String enc(Object v) {
            if (v == null) return "null";
            if (v instanceof Boolean || v instanceof Number) return v.toString();
            if (v instanceof CharSequence) return "\"" + esc(String.valueOf(v)) + "\"";
            if (v instanceof Vector) {
                @SuppressWarnings("unchecked")
                Vector<Object> vec = (Vector<Object>) v;
                StringBuilder b = new StringBuilder("[");
                for (int i = 0; i < vec.size(); i++) {
                    if (i > 0) b.append(",");
                    b.append(enc(vec.get(i)));
                }
                b.append("]");
                return b.toString();
            }
            return "\"" + esc(String.valueOf(v)) + "\"";
        }

        static String esc(String s) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '\\': b.append("\\\\"); break;
                    case '\"': b.append("\\\""); break;
                    case '\n': b.append("\\n");  break;
                    case '\r': b.append("\\r");  break;
                    case '\t': b.append("\\t");  break;
                    default:
                        if (c < 0x20) b.append(String.format("\\u%04x", (int)c));
                        else b.append(c);
                }
            }
            return b.toString();
        }

        static String getString(String json, String field) {
            String needle = "\"" + field + "\":\"";
            int i = json.indexOf(needle);
            if (i < 0) return null;
            int p = i + needle.length();
            StringBuilder out = new StringBuilder();
            boolean esc = false;
            for (; p < json.length(); p++) {
                char c = json.charAt(p);
                if (esc) { out.append(c); esc = false; continue; }
                if (c == '\\') { esc = true; continue; }
                if (c == '"') break;
                out.append(c);
            }
            return out.toString();
        }

        static Object[] getArray(String json, String field) {
            String needle = "\"" + field + "\":[";
            int i = json.indexOf(needle);
            if (i < 0) return new Object[0];
            int p = i + needle.length();
            ArrayList<Object> out = new ArrayList<>();
            StringBuilder tok = new StringBuilder();
            boolean inStr = false, esc = false;

            for (; p < json.length(); p++) {
                char c = json.charAt(p);
                if (inStr) {
                    if (esc) { tok.append(c); esc = false; continue; }
                    if (c == '\\') { esc = true; continue; }
                    if (c == '"') {
                        out.add(tok.toString());
                        tok.setLength(0);
                        inStr = false;
                        continue;
                    }
                    tok.append(c);
                    continue;
                }
                if (c == '"') { inStr = true; continue; }
                if (c == ']') {
                    String t = tok.toString().trim();
                    if (!t.isEmpty()) out.add(parsePrim(t));
                    break;
                }
                if (c == ',') {
                    String t = tok.toString().trim();
                    if (!t.isEmpty()) out.add(parsePrim(t));
                    tok.setLength(0);
                    continue;
                }
                tok.append(c);
            }
            return out.toArray();
        }

        private static Object parsePrim(String t) {
            if (t.equals("true")) return Boolean.TRUE;
            if (t.equals("false")) return Boolean.FALSE;
            if (t.equals("null")) return null;
            try { return Integer.parseInt(t); } catch (Exception ignored) {}
            return t;
        }
    }
}
