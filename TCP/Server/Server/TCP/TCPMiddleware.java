package Server.TCP;

import Server.Common.Trace;
import Server.Middleware.Middleware;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Vector;
import java.util.ArrayList;

public class TCPMiddleware {

    private static volatile boolean running = true;

    private final Middleware mw;
    private final int listenPort;
    private ServerSocket serverSocket;

    public TCPMiddleware(int listenPort,
                         String flightHost, int flightPort,
                         String carHost, int carPort,
                         String roomHost, int roomPort) throws IOException {
        this.listenPort = listenPort;
        this.mw = new Middleware("Middleware", flightHost, flightPort, carHost, carPort, roomHost, roomPort);
    }

    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: java Server.TCP.TCPMiddleware <listen_port> <flightHost:port> <carHost:port> <roomHost:port>");
            System.exit(1);
        }

        try {
            int listen = Integer.parseInt(args[0]);

            String[] f = args[1].split(":", 2);
            String[] c = args[2].split(":", 2);
            String[] r = args[3].split(":", 2);

            String flightHost = f[0];
            int flightPort = Integer.parseInt(f[1]);

            String carHost = c[0];
            int carPort = Integer.parseInt(c[1]);

            String roomHost = r[0];
            int roomPort = Integer.parseInt(r[1]);

            TCPMiddleware server = new TCPMiddleware(listen, flightHost, flightPort, carHost, carPort, roomHost, roomPort);

            Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "TCPMiddleware-ShutdownHook"));

            server.start();
        } catch (Exception e) {
            System.err.println("\u001b[31;1mMiddleware exception: \u001b[0m" + e);
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(listenPort);
        System.out.println("Middleware listening on :" + listenPort);

        while (running) {
            try {
                Socket s = serverSocket.accept();
                s.setKeepAlive(true);
                new Thread(new ClientHandler(s, mw), "ClientHandler-" + s.getRemoteSocketAddress()).start();
            } catch (IOException e) {
                if (running) {
                    Trace.warn("Accept failed: " + e.getMessage());
                }
            }
        }
    }

    public void stop() {
        running = false;
        System.out.println("Shutting down Middleware ...");
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
        } catch (IOException ignored) { }
        try {
            mw.close();
        } catch (Exception ignored) { }
        System.out.println("Middleware stopped.");
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private final Middleware mw;

        ClientHandler(Socket socket, Middleware mw) {
            this.socket = socket;
            this.mw = mw;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = in.readLine()) != null) {
                    Request req;
                    try {
                        req = Request.parse(line);
                    } catch (Exception pe) {
                        writeError(out, "Malformed request");
                        continue;
                    }

                    Object result;
                    boolean ok = true;
                    String errMsg = null;

                    try {
                        result = dispatch(req);
                    } catch (Exception e) {
                        ok = false;
                        result = null;
                        errMsg = e.getMessage() == null ? "Operation failed" : e.getMessage();
                    }

                    if (ok) {
                        writeOk(out, result);
                    } else {
                        writeFailed(out, errMsg);
                    }
                }

            } catch (IOException e) {
                // connection closed or error; may need to handle later
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private Object dispatch(Request req) throws Exception {
            String m = req.method;
            Object[] a = req.args;

            switch (m) {
                case "addFlight":
                    return mw.addFlight(toInt(a,0), toInt(a,1), toInt(a,2));
                case "deleteFlight":
                    return mw.deleteFlight(toInt(a,0));
                case "queryFlight":
                    return mw.queryFlight(toInt(a,0));
                case "queryFlightPrice":
                    return mw.queryFlightPrice(toInt(a,0));

                case "addCars":
                    return mw.addCars(toStr(a,0), toInt(a,1), toInt(a,2));
                case "deleteCars":
                    return mw.deleteCars(toStr(a,0));
                case "queryCars":
                    return mw.queryCars(toStr(a,0));
                case "queryCarsPrice":
                    return mw.queryCarsPrice(toStr(a,0));

                case "addRooms":
                    return mw.addRooms(toStr(a,0), toInt(a,1), toInt(a,2));
                case "deleteRooms":
                    return mw.deleteRooms(toStr(a,0));
                case "queryRooms":
                    return mw.queryRooms(toStr(a,0));
                case "queryRoomsPrice":
                    return mw.queryRoomsPrice(toStr(a,0));

                case "newCustomer":
                    return mw.newCustomer();
                case "newCustomerID":
                    return mw.newCustomer(toInt(a,0));
                case "deleteCustomer":
                    return mw.deleteCustomer(toInt(a,0));
                case "queryCustomer":
                    return mw.queryCustomerInfo(toInt(a,0));

                case "reserveFlight":
                    return mw.reserveFlight(toInt(a,0), toInt(a,1));
                case "reserveCar":
                    return mw.reserveCar(toInt(a,0), toStr(a,1));
                case "reserveRoom":
                    return mw.reserveRoom(toInt(a,0), toStr(a,1));

                case "bundle": {
                    if (a.length < 5) {
                        throw new IllegalArgumentException("bundle requires 5 args: customerID, flights[], location, car, room");
                    }
                    int customerID = toInt(a,0);
                    @SuppressWarnings("unchecked")
                    Vector<String> flights = toStringVector(a[1]);
                    String location = toStr(a,2);
                    boolean car = toBool(a,3);
                    boolean room = toBool(a,4);
                    return mw.bundle(customerID, flights, location, car, room);
                }

                case "getName":
                    return mw.getName();

                default:
                    throw new IllegalArgumentException("Unknown method: " + m);
            }
        }

        private static int toInt(Object[] a, int i) { return ((Number)a[i]).intValue(); }
        private static boolean toBool(Object[] a, int i) {
            if (a[i] instanceof Boolean) return (Boolean)a[i];
            if (a[i] instanceof String)  return Boolean.parseBoolean((String)a[i]);
            throw new IllegalArgumentException("Expected boolean");
        }
        private static String toStr(Object[] a, int i) { return String.valueOf(a[i]); }

        @SuppressWarnings("unchecked")
        private static Vector<String> toStringVector(Object obj) {
            if (obj instanceof Vector) return (Vector<String>) obj;
            if (obj instanceof Object[]) {
                Vector<String> v = new Vector<>();
                for (Object o : (Object[])obj) v.add(String.valueOf(o));
                return v;
            }
            // our parser returns Object[] for arrays; strings inside
            if (obj instanceof java.util.List) {
                Vector<String> v = new Vector<>();
                for (Object o : (java.util.List<?>)obj) v.add(String.valueOf(o));
                return v;
            }
            if (obj instanceof String[]) {
                Vector<String> v = new Vector<>();
                for (String s : (String[])obj) v.add(s);
                return v;
            }
            Vector<String> v = new Vector<>();
            v.add(String.valueOf(obj));
            return v;
        }

        private static void writeOk(BufferedWriter out, Object response) throws IOException {
            String respJson = JsonUtil.success(response);
            out.write(respJson);
            out.write("\n");
            out.flush();
        }

        private static void writeFailed(BufferedWriter out, String message) throws IOException {
            String respJson = JsonUtil.failed(message);
            out.write(respJson);
            out.write("\n");
            out.flush();
        }

        private static void writeError(BufferedWriter out, String message) throws IOException {
            writeFailed(out, message);
        }
    }

    private static class Request {
        final String method;
        final Object[] args;

        private Request(String method, Object[] args) {
            this.method = method;
            this.args = args;
        }

        static Request parse(String s) {
            String noWS = s.trim();

            String method = JsonUtil.extractStringField(noWS, "method");
            Object[] args = JsonUtil.extractArrayField(noWS, "args");

            if (method == null || args == null) throw new IllegalArgumentException("Malformed JSON");
            return new Request(method, args);
        }
    }

    private static class JsonUtil {
        private static String esc(String s) {
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
                        if (c < 0x20) {
                            b.append(String.format("\\u%04x", (int)c));
                        } else {
                            b.append(c);
                        }
                }
            }
            return b.toString();
        }

        static String success(Object response) {
            String respValue = encodeValue(response);
            return "{\"status\":\"ok\",\"response\":" + respValue + "}";
        }
        static String failed(String message) {
            return "{\"status\":\"failed\",\"message\":\"" + esc(message == null ? "Operation failed" : message) + "\"}";
        }

        static String encodeValue(Object v) {
            if (v == null) return "null";
            if (v instanceof Boolean || v instanceof Number) return v.toString();
            if (v instanceof CharSequence) return "\"" + esc(String.valueOf(v)) + "\"";
            if (v instanceof String[]) {
                StringBuilder b = new StringBuilder("[");
                String[] arr = (String[]) v;
                for (int i = 0; i < arr.length; i++) {
                    if (i > 0) b.append(",");
                    b.append("\"").append(esc(arr[i])).append("\"");
                }
                b.append("]");
                return b.toString();
            }
            if (v instanceof Vector) {
                @SuppressWarnings("unchecked")
                Vector<Object> vec = (Vector<Object>) v;
                StringBuilder b = new StringBuilder("[");
                for (int i = 0; i < vec.size(); i++) {
                    if (i > 0) b.append(",");
                    b.append(encodeValue(vec.get(i)));
                }
                b.append("]");
                return b.toString();
            }
            return "\"" + esc(String.valueOf(v)) + "\"";
        }

        static String extractStringField(String json, String field) {
            String needle = "\"" + field + "\":\"";
            int i = json.indexOf(needle);
            if (i < 0) return null;
            int start = i + needle.length();
            StringBuilder sb = new StringBuilder();
            boolean esc = false;
            for (int p = start; p < json.length(); p++) {
                char c = json.charAt(p);
                if (esc) { sb.append(c); esc = false; continue; }
                if (c == '\\') { esc = true; continue; }
                if (c == '"') { return sb.toString(); }
                sb.append(c);
            }
            return null;
        }

        static Object[] extractArrayField(String json, String field) {
            String needle = "\"" + field + "\":[";
            int i = json.indexOf(needle);
            if (i < 0) return new Object[0];

            int p = i + needle.length();
            ArrayList<Object> out = new ArrayList<>();
            StringBuilder tok = new StringBuilder();
            boolean inStr = false, esc = false;
            int depth = 0; // track nested [] or {}

            for (; p < json.length(); p++) {
                char c = json.charAt(p);

                if (inStr) {
                    if (esc) { tok.append(c); esc = false; continue; }
                    if (c == '\\') { esc = true; continue; }
                    if (c == '"') { inStr = false; continue; }
                    tok.append(c);
                    continue;
                }

                if (c == '"') { inStr = true; continue; }

                if (c == '[' || c == '{') { depth++; tok.append(c); continue; }
                if (c == ']' || c == '}') {
                    if (depth > 0) { depth--; tok.append(c); continue; }
                    String t = tok.toString().trim();
                    if (!t.isEmpty()) out.add(parseTopLevelArg(t));
                    break;
                }

                if (c == ',' && depth == 0) {
                    String t = tok.toString().trim();
                    if (!t.isEmpty()) out.add(parseTopLevelArg(t));
                    tok.setLength(0);
                    continue;
                }

                tok.append(c);
            }

            return out.toArray();
        }

        private static Object parseTopLevelArg(String t) {
            if ("true".equals(t)) return Boolean.TRUE;
            if ("false".equals(t)) return Boolean.FALSE;
            if ("null".equals(t)) return null;
            try {
                if (!t.isEmpty() && (t.charAt(0) == '-' || Character.isDigit(t.charAt(0)))) {
                    return Integer.parseInt(t);
                }
            } catch (Exception ignored) {}

            if (t.startsWith("[") && t.endsWith("]")) {
                String inner = t.substring(1, t.length() - 1).trim();
                ArrayList<String> list = new ArrayList<>();
                StringBuilder sb = new StringBuilder();
                boolean inStr = false, esc = false;
                for (int i = 0; i < inner.length(); i++) {
                    char c = inner.charAt(i);
                    if (inStr) {
                        if (esc) { sb.append(c); esc = false; continue; }
                        if (c == '\\') { esc = true; continue; }
                        if (c == '"') { inStr = false; continue; }
                        sb.append(c);
                        continue;
                    }
                    if (c == '"') { inStr = true; continue; }
                    if (c == ',') { list.add(sb.toString()); sb.setLength(0); continue; }
                    sb.append(c);
                }
                if (!sb.isEmpty()) list.add(sb.toString());
                Vector<String> v = new Vector<>();
                for (String s : list) v.add(s.trim());
                return v;
            }

            return t;
        }

        private static Object parsePrimitive(String t) {
            if (t.equals("true")) return Boolean.TRUE;
            if (t.equals("false")) return Boolean.FALSE;
            if (t.equals("null")) return null;
            try {
                if (t.startsWith("-") || Character.isDigit(t.charAt(0))) {
                    return Integer.parseInt(t);
                }
            } catch (Exception ignored) {}
            return t;
        }
    }
}
