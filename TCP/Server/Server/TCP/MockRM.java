package Server.TCP;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MockRM {

    public enum RMType { Flights, Cars, Rooms }

    private final RMType type;
    private final int port;

    private static final class Item {
        int count;
        int price;
        Item(int c, int p) { count = c; price = p; }
    }

    private final ConcurrentHashMap<Integer, Item> flights = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Item> cars    = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Item> rooms   = new ConcurrentHashMap<>();

    public MockRM(RMType type, int port) {
        this.type = type;
        this.port = port;
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Server.TCP.MockRM <Flights|Cars|Rooms> <port>");
            System.exit(1);
        }

        RMType t;
        try {
            t = RMType.valueOf(args[0]);
        } catch (Exception e) {
            System.err.println("type must be one of: Flights | Cars | Rooms");
            return;
        }

        int p = 0;
        try {
            p = Integer.parseInt(args[1]);
        } catch (NumberFormatException ignored) {}

        try {
            new MockRM(t, p).start();
        } catch (IOException e) {
            System.err.println("[MockRM] fatal: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void start() throws IOException {
        ServerSocket server = new ServerSocket(port);
        System.out.println("[MockRM-" + type + "] listening on :" + port);
        while (true) {
            Socket s = server.accept();
            s.setTcpNoDelay(true);
            System.out.println("[MockRM-" + type + "] client " + s.getRemoteSocketAddress());
            new Thread(new ClientHandler(s), "mockrm-" + type + "-" + s.getPort()).start();
        }
    }

    private final class ClientHandler implements Runnable {
        private final Socket sock;

        ClientHandler(Socket s) { this.sock = s; }

        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter out = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = in.readLine()) != null) {
                    String method = parseMethod(line);
                    Object[] args = parseArgs(line);
                    System.out.println("[MockRM-" + type + "] <- " + method + " " + Arrays.toString(args));

                    String resp;
                    try {
                        resp = handle(method, args);
                    } catch (Exception e) {
                        resp = failed("Exception while handling '" + method + "': " + e.getMessage());
                    }

                    out.write(resp);
                    out.write("\n");
                    out.flush();
                    System.out.println("[MockRM-" + type + "] -> " + resp);
                }
            } catch (IOException e) {
                System.out.println("[MockRM-" + type + "] client disconnected: " + e.getMessage());
            } finally {
                try { sock.close(); } catch (IOException ignored) {}
            }
        }
    }

    private String handle(String method, Object[] a) {
        if (method == null || method.isEmpty()) return failed("Missing method");

        switch (method) {
            case "getName":
                return okString("MockRM-" + type.name());

            case "addFlight": {
                requireType(RMType.Flights);
                int flightNum = toInt(a,0), seats = toInt(a,1), price = toInt(a,2);
                flights.compute(flightNum, (k, v) -> {
                    if (v == null) return new Item(seats, price);
                    v.count += seats;
                    if (price > 0) v.price = price;
                    return v;
                });
                return okBool(true);
            }
            case "deleteFlight": {
                requireType(RMType.Flights);
                int flightNum = toInt(a,0);
                Item it = flights.get(flightNum);
                if (it == null) return okBool(false);
                if (it.count >= 0) {
                    flights.remove(flightNum);
                    return okBool(true);
                }
                return okBool(false);
            }
            case "queryFlight": {
                requireType(RMType.Flights);
                int flightNum = toInt(a,0);
                Item it = flights.get(flightNum);
                return okInt(it == null ? 0 : it.count);
            }
            case "queryFlightPrice": {
                requireType(RMType.Flights);
                int flightNum = toInt(a,0);
                Item it = flights.get(flightNum);
                return okInt(it == null ? 0 : it.price);
            }
            case "reserveFlight": {
                requireType(RMType.Flights);
                int flightNum = toInt(a,1);
                Item it = flights.get(flightNum);
                if (it == null || it.count <= 0) return okBool(false);
                it.count -= 1;
                return okBool(true);
            }

            case "addCars": {
                requireType(RMType.Cars);
                String loc = toStr(a,0); int count = toInt(a,1); int price = toInt(a,2);
                cars.compute(loc, (k,v) -> {
                    if (v == null) return new Item(count, price);
                    v.count += count;
                    if (price > 0) v.price = price;
                    return v;
                });
                return okBool(true);
            }
            case "deleteCars": {
                requireType(RMType.Cars);
                String loc = toStr(a,0);
                Item it = cars.remove(loc);
                return okBool(it != null);
            }
            case "queryCars": {
                requireType(RMType.Cars);
                String loc = toStr(a,0);
                Item it = cars.get(loc);
                return okInt(it == null ? 0 : it.count);
            }
            case "queryCarsPrice": {
                requireType(RMType.Cars);
                String loc = toStr(a,0);
                Item it = cars.get(loc);
                return okInt(it == null ? 0 : it.price);
            }
            case "reserveCar": {
                requireType(RMType.Cars);
                String loc = toStr(a,1);
                Item it = cars.get(loc);
                if (it == null || it.count <= 0) return okBool(false);
                it.count -= 1;
                return okBool(true);
            }

            case "addRooms": {
                requireType(RMType.Rooms);
                String loc = toStr(a,0); int count = toInt(a,1); int price = toInt(a,2);
                rooms.compute(loc, (k,v) -> {
                    if (v == null) return new Item(count, price);
                    v.count += count;
                    if (price > 0) v.price = price;
                    return v;
                });
                return okBool(true);
            }
            case "deleteRooms": {
                requireType(RMType.Rooms);
                String loc = toStr(a,0);
                Item it = rooms.remove(loc);
                return okBool(it != null);
            }
            case "queryRooms": {
                requireType(RMType.Rooms);
                String loc = toStr(a,0);
                Item it = rooms.get(loc);
                return okInt(it == null ? 0 : it.count);
            }
            case "queryRoomsPrice": {
                requireType(RMType.Rooms);
                String loc = toStr(a,0);
                Item it = rooms.get(loc);
                return okInt(it == null ? 0 : it.price);
            }
            case "reserveRoom": {
                requireType(RMType.Rooms);
                String loc = toStr(a,1);
                Item it = rooms.get(loc);
                if (it == null || it.count <= 0) return okBool(false);
                it.count -= 1;
                return okBool(true);
            }

            default:
                return failed("Unknown method for " + type + ": " + method);
        }
    }

    // helpers
    private static String okBool(boolean v)   { return "{\"status\":\"ok\",\"response\":" + (v ? "true" : "false") + "}"; }
    private static String okInt(int v)        { return "{\"status\":\"ok\",\"response\":" + v + "}"; }
    private static String okString(String v)  { return "{\"status\":\"ok\",\"response\":\"" + esc(v) + "\"}"; }
    private static String failed(String msg)  { return "{\"status\":\"failed\",\"message\":\"" + esc(msg) + "\"}"; }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void requireType(RMType expected) {
        // may need in future
    }

    private static int toInt(Object[] a, int i) {
        Object o = a[i];
        if (o instanceof Number) return ((Number) o).intValue();
        return Integer.parseInt(String.valueOf(o));
    }
    private static String toStr(Object[] a, int i) { return String.valueOf(a[i]); }

    private static String parseMethod(String json) {
        String needle = "\"method\":\"";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        int p = i + needle.length();
        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        for (; p < json.length(); p++) {
            char c = json.charAt(p);
            if (esc) { sb.append(c); esc = false; continue; }
            if (c == '\\') { esc = true; continue; }
            if (c == '"') break;
            sb.append(c);
        }
        return sb.toString();
    }

    private static Object[] parseArgs(String json) {
        String needle = "\"args\":[";
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
                if (c == '"') { // end string
                    out.add(tok.toString());
                    tok.setLength(0);
                    inStr = false;
                } else tok.append(c);
                continue;
            }
            if (c == '"') { inStr = true; continue; }
            if (c == ']') {
                String t = tok.toString().trim();
                if (!t.isEmpty()) out.add(parsePrimitive(t));
                break;
            }
            if (c == ',') {
                String t = tok.toString().trim();
                if (!t.isEmpty()) out.add(parsePrimitive(t));
                tok.setLength(0);
                continue;
            }
            tok.append(c);
        }
        return out.toArray();
    }

    private static Object parsePrimitive(String t) {
        if (t.equals("true"))  return Boolean.TRUE;
        if (t.equals("false")) return Boolean.FALSE;
        if (t.equals("null"))  return null;
        try {
            if (t.startsWith("-") || Character.isDigit(t.charAt(0))) return Integer.parseInt(t);
        } catch (Exception ignored) {}
        return t;
    }
}
