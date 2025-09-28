package Server.TCP;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MockMiddleware {

    private static final AtomicInteger CID = new AtomicInteger(10000);

    public static void main(String[] args) {
        int port = 5000;
        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); } catch (NumberFormatException ignore) {}
        }

        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("[MockMiddleware] listening on :" + port);

            while (true) {
                final Socket s = server.accept();
                s.setTcpNoDelay(true);
                System.out.println("[MockMiddleware] client connected from " + s.getRemoteSocketAddress());
                new Thread(new ClientHandler(s), "mm-client-" + s.getPort()).start();
            }
        } catch (IOException e) {
            System.err.println("[MockMiddleware] fatal: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static final class ClientHandler implements Runnable {
        private final Socket sock;

        ClientHandler(Socket s) { this.sock = s; }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), "UTF-8"));
                 PrintWriter out = new PrintWriter(new OutputStreamWriter(sock.getOutputStream(), "UTF-8"), true)) {

                String line;
                while ((line = in.readLine()) != null) {
                    String method = parseMethod(line);
                    List<String> args = parseArgs(line);

                    System.out.println("[MockMiddleware] <- " + method + " " + args);

                    String resp;
                    try {
                        resp = handle(method, args);
                    } catch (Exception ex) {
                        resp = fail("Exception while handling '" + method + "': " + ex.getMessage());
                    }

                    out.println(resp);
                    System.out.println("[MockMiddleware] -> " + resp);
                }
            } catch (IOException ioe) {
                System.out.println("[MockMiddleware] client disconnected: " + ioe.getMessage());
            } finally {
                try { sock.close(); } catch (IOException ignore) {}
            }
        }

        private String handle(String method, List<String> args) {
            if (method == null || method.isEmpty()) return fail("Missing method");

            switch (method) {
                case "getName":
                    return okString("MockMiddleware");

                case "newCustomer":
                    return okInt(CID.incrementAndGet());

                case "newCustomerID":
                    return okBool(true);

                case "addFlight":
                case "addCars":
                case "addRooms":
                case "deleteFlight":
                case "deleteCars":
                case "deleteRooms":
                case "deleteCustomer":
                case "reserveFlight":
                case "reserveCar":
                case "reserveRoom":
                case "bundle":
                    return okBool(true);

                case "queryFlight":
                case "queryCars":
                case "queryRooms":
                    return okInt(42);

                case "queryFlightPrice":
                case "queryCarsPrice":
                case "queryRoomsPrice":
                    return okInt(199);

                case "queryCustomer":
                    String who = (args.size() > 0 ? args.get(0) : "unknown");
                    String bill = "Customer " + who + ":\n  - mock charge: $123\n";
                    return okString(bill);

                default:
                    return fail("Unknown method: " + method);
            }
        }
    }

    // helpers
    private static String okBool(boolean b) {
        return "{\"status\":\"succeeded\",\"response\":" + (b ? "true" : "false") + ",\"responseType\":\"boolean\",\"message\":\"\"}";
    }

    private static String okInt(int n) {
        return "{\"status\":\"succeeded\",\"response\":" + n + ",\"responseType\":\"int\",\"message\":\"\"}";
    }

    private static String okString(String s) {
        return "{\"status\":\"succeeded\",\"response\":\"" + esc(s) + "\",\"responseType\":\"string\",\"message\":\"\"}";
    }

    private static String fail(String msg) {
        return "{\"status\":\"failed\",\"response\":null,\"responseType\":\"\",\"message\":\"" + esc(msg) + "\"}";
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String parseMethod(String json) {
        int i = json.indexOf("\"method\"");
        if (i < 0) return null;
        int c = json.indexOf(':', i);
        if (c < 0) return null;
        int q1 = json.indexOf('"', c + 1);
        if (q1 < 0) return null;
        int q2 = nextQuote(json, q1 + 1);
        if (q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }

    private static List<String> parseArgs(String json) {
        List<String> out = new ArrayList<>();
        int i = json.indexOf("\"args\"");
        if (i < 0) return out;
        int c = json.indexOf(':', i);
        if (c < 0) return out;
        int lb = json.indexOf('[', c + 1);
        if (lb < 0) return out;
        int rb = matchBracket(json, lb);
        if (rb < 0) return out;
        String body = json.substring(lb + 1, rb).trim();
        if (body.isEmpty()) return out;

        boolean inStr = false;
        StringBuilder cur = new StringBuilder();
        for (int p = 0; p < body.length(); p++) {
            char ch = body.charAt(p);
            if (ch == '"' && (p == 0 || body.charAt(p - 1) != '\\')) {
                inStr = !inStr;
                continue;
            }
            if (ch == ',' && !inStr) {
                out.add(unquote(cur.toString().trim()));
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        if (cur.length() > 0) out.add(unquote(cur.toString().trim()));
        return out;
    }

    private static int nextQuote(String s, int from) {
        boolean esc = false;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc) { esc = false; continue; }
            if (c == '\\') { esc = true; continue; }
            if (c == '"') return i;
        }
        return -1;
    }

    private static int matchBracket(String s, int openIdx) {
        int depth = 0;
        boolean inStr = false;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) inStr = !inStr;
            if (inStr) continue;
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static String unquote(String x) {
        String s = x;
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
