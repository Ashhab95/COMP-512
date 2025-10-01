package Client;

import java.io.*;
import java.net.Socket;
import java.util.Vector;

public class TCPClient {

    private static String s_serverHost = "localhost";
    private static int    s_serverPort = 5000;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public TCPClient(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
        System.out.println("Connected to middleware [" + host + ":" + port + "]");
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            s_serverHost = args[0];
        }
        if (args.length > 1) {
            try {
                s_serverPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException nfe) {
                System.err.println((char)27 + "[31;1mClient exception: " + (char)27 + "[0mUsage: java Client.TCPClient [server_hostname [server_port]]");
                System.exit(1);
            }
        }
        if (args.length > 2) {
            System.err.println((char)27 + "[31;1mClient exception: " + (char)27 + "[0mUsage: java Client.TCPClient [server_hostname [server_port]]");
            System.exit(1);
        }

        try {
            boolean first = true;
            while (true) {
                try {
                    TCPClient transport = new TCPClient(s_serverHost, s_serverPort);
                    Client client = new Client(transport);
                    client.start();
                    break;
                } catch (IOException ioe) {
                    if (first) {
                        System.out.println("Waiting for middleware [" + s_serverHost + ":" + s_serverPort + "]");
                        first = false;
                    }
                    try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                }
            }
        } catch (Exception e) {
            System.err.println((char)27 + "[31;1mClient exception: " + (char)27 + "[0mUncaught exception");
            e.printStackTrace();
            System.exit(1);
        }
    }

    // helper
    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String send(String method, Object... args) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"method\":\"").append(esc(method)).append("\",\"args\":[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(",");
            Object a = args[i];
            if (a instanceof Number || a instanceof Boolean) {
                sb.append(a.toString());
            } else if (a instanceof Vector) {
                @SuppressWarnings("unchecked")
                Vector<String> v = (Vector<String>) a;
                sb.append("[");
                for (int j = 0; j < v.size(); j++) {
                    if (j > 0) sb.append(",");
                    sb.append("\"").append(esc(v.get(j))).append("\"");
                }
                sb.append("]");
            } else {
                sb.append("\"").append(esc(String.valueOf(a))).append("\"");
            }
        }
        sb.append("]}");

        out.println(sb.toString());
        String resp = in.readLine();
        if (resp == null) throw new IOException("Connection closed by server");
        return resp;
    }

    private static void ensureOk(String json) throws IOException {
        String t = json.replaceAll("\\s+", "");
        if (t.contains("\"status\":\"failed\"")) {
            String msg = fieldString(t, "message");
            throw new IOException(msg == null ? "Operation failed" : msg);
        }
    }

    private static boolean boolResp(String json) throws IOException {
        ensureOk(json);
        String t = json.replaceAll("\\s+", "");
        return t.contains("\"response\":true");
    }

    private static int intResp(String json) throws IOException {
        ensureOk(json);
        String t = json.replaceAll("\\s+", "");
        int idx = t.indexOf("\"response\":");
        if (idx < 0) throw new IOException("Malformed response");
        int p = idx + 11;
        StringBuilder sb = new StringBuilder();
        while (p < t.length() && (Character.isDigit(t.charAt(p)) || t.charAt(p) == '-')) {
            sb.append(t.charAt(p++));
        }
        try {
            return Integer.parseInt(sb.toString());
        } catch (Exception e) {
            throw new IOException("Expected integer response");
        }
    }

    private static String stringResp(String raw) throws IOException {
        ensureOk(raw);

        int k = raw.indexOf("\"response\":\"");
        if (k < 0) {
            if (raw.contains("\"response\":null")) {
                return "";
            }
            throw new IOException("Expected string response");
        }

        int p = k + 12;
        StringBuilder sb = new StringBuilder();
        boolean esc = false;

        while (p < raw.length()) {
            char c = raw.charAt(p++);
            if (esc) {
                // handle common escapes
                switch (c) {
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case '"': sb.append('\"'); break;
                    case '\\': sb.append('\\'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    default:  sb.append(c); break; // fallback
                }
                esc = false;
                continue;
            }
            if (c == '\\') { esc = true; continue; }
            if (c == '"')  { break; } // end of the JSON string
            sb.append(c);
        }

        return sb.toString();
    }

    private static String fieldString(String t, String key) {
        String patt = "\"" + key + "\":\"";
        int i = t.indexOf(patt);
        if (i < 0) return null;
        int p = i + patt.length();
        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        while (p < t.length()) {
            char c = t.charAt(p++);
            if (esc) { sb.append(c); esc = false; continue; }
            if (c == '\\') { esc = true; continue; }
            if (c == '"') break;
            sb.append(c);
        }
        return sb.toString();
    }

    // methods used by Client

    public boolean addFlight(int flightNum, int flightSeats, int flightPrice) throws IOException {
        return boolResp(send("addFlight", flightNum, flightSeats, flightPrice));
    }

    public boolean addCars(String location, int numCars, int price) throws IOException {
        return boolResp(send("addCars", location, numCars, price));
    }

    public boolean addRooms(String location, int numRooms, int price) throws IOException {
        return boolResp(send("addRooms", location, numRooms, price));
    }

    public int newCustomer() throws IOException {
        return intResp(send("newCustomer"));
    }

    public boolean newCustomer(int cid) throws IOException {
        return boolResp(send("newCustomerID", cid));
    }

    public boolean deleteFlight(int flightNum) throws IOException {
        return boolResp(send("deleteFlight", flightNum));
    }

    public boolean deleteCars(String location) throws IOException {
        return boolResp(send("deleteCars", location));
    }

    public boolean deleteRooms(String location) throws IOException {
        return boolResp(send("deleteRooms", location));
    }

    public boolean deleteCustomer(int customerID) throws IOException {
        return boolResp(send("deleteCustomer", customerID));
    }

    public int queryFlight(int flightNumber) throws IOException {
        return intResp(send("queryFlight", flightNumber));
    }

    public int queryCars(String location) throws IOException {
        return intResp(send("queryCars", location));
    }

    public int queryRooms(String location) throws IOException {
        return intResp(send("queryRooms", location));
    }

    public String queryCustomerInfo(int customerID) throws IOException {
        // server method name aligned with your RMI client behavior
        return stringResp(send("queryCustomer", customerID));
    }

    public int queryFlightPrice(int flightNumber) throws IOException {
        return intResp(send("queryFlightPrice", flightNumber));
    }

    public int queryCarsPrice(String location) throws IOException {
        return intResp(send("queryCarsPrice", location));
    }

    public int queryRoomsPrice(String location) throws IOException {
        return intResp(send("queryRoomsPrice", location));
    }

    public boolean reserveFlight(int customerID, int flightNumber) throws IOException {
        return boolResp(send("reserveFlight", customerID, flightNumber));
    }

    public boolean reserveCar(int customerID, String location) throws IOException {
        return boolResp(send("reserveCar", customerID, location));
    }

    public boolean reserveRoom(int customerID, String location) throws IOException {
        return boolResp(send("reserveRoom", customerID, location));
    }

    public boolean bundle(int customerID, Vector<String> flightNumbers, String location, boolean car, boolean room) throws IOException {
        return boolResp(send("bundle", customerID, flightNumbers, location, car, room));
    }

    public String getName() throws IOException {
        return stringResp(send("getName"));
    }
}
