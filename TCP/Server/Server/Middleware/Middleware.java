package Server.Middleware;

import Server.Common.*;

import java.io.Console;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Middleware extends ResourceManager {

    protected final TCPResourceManagerClient flightRM;
    protected final TCPResourceManagerClient carRM;
    protected final TCPResourceManagerClient roomRM;

    private final ConcurrentHashMap<Integer, Customer> customers = new ConcurrentHashMap<>();

    private final Random rng = new Random();

    public Middleware(String name,
                      String flightHost, int flightPort,
                      String carHost, int carPort,
                      String roomHost, int roomPort) throws IOException {
        super(name);
        this.flightRM = new TCPResourceManagerClient(flightHost, flightPort, "Flights");
        this.carRM    = new TCPResourceManagerClient(carHost,    carPort,    "Cars");
        this.roomRM   = new TCPResourceManagerClient(roomHost,   roomPort,   "Rooms");
    }

    public void close() {
        try { flightRM.close(); } catch (Exception ignored) {}
        try { carRM.close(); }    catch (Exception ignored) {}
        try { roomRM.close(); }   catch (Exception ignored) {}
    }

    @Override
    public boolean addFlight(int flightNum, int flightSeats, int flightPrice) {
        Trace.info("MW::addFlight(" + flightNum + "," + flightSeats + ",$" + flightPrice + ")");
        try {
            return flightRM.sendBool("addFlight", flightNum, flightSeats, flightPrice);
        } catch (IOException e) {
            Trace.warn("MW::addFlight failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean deleteFlight(int flightNum) {
        Trace.info("MW::deleteFlight(" + flightNum + ")");
        try {
            return flightRM.sendBool("deleteFlight", flightNum);
        } catch (IOException e) {
            Trace.warn("MW::deleteFlight failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public int queryFlight(int flightNum) {
        Trace.info("MW::queryFlight(" + flightNum + ")");
        try {
            return flightRM.sendInt("queryFlight", flightNum);
        } catch (IOException e) {
            Trace.warn("MW::queryFlight failed: " + e.getMessage());
            return -1;
        }
    }

    @Override
    public int queryFlightPrice(int flightNum) {
        Trace.info("MW::queryFlightPrice(" + flightNum + ")");
        try {
            return flightRM.sendInt("queryFlightPrice", flightNum);
        } catch (IOException e) {
            Trace.warn("MW::queryFlightPrice failed: " + e.getMessage());
            return -1;
        }
    }

    @Override
    public boolean addCars(String location, int numCars, int price) {
        Trace.info("MW::addCars(" + location + "," + numCars + ",$" + price + ")");
        try {
            return carRM.sendBool("addCars", location, numCars, price);
        } catch (IOException e) {
            Trace.warn("MW::addCars failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean deleteCars(String location) {
        Trace.info("MW::deleteCars(" + location + ")");
        try {
            return carRM.sendBool("deleteCars", location);
        } catch (IOException e) {
            Trace.warn("MW::deleteCars failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public int queryCars(String location) {
        Trace.info("MW::queryCars(" + location + ")");
        try {
            return carRM.sendInt("queryCars", location);
        } catch (IOException e) {
            Trace.warn("MW::queryCars failed: " + e.getMessage());
            return -1;
        }
    }

    @Override
    public int queryCarsPrice(String location) {
        Trace.info("MW::queryCarsPrice(" + location + ")");
        try {
            return carRM.sendInt("queryCarsPrice", location);
        } catch (IOException e) {
            Trace.warn("MW::queryCarsPrice failed: " + e.getMessage());
            return -1;
        }
    }

    @Override
    public boolean addRooms(String location, int numRooms, int price) {
        Trace.info("MW::addRooms(" + location + "," + numRooms + ",$" + price + ")");
        try {
            return roomRM.sendBool("addRooms", location, numRooms, price);
        } catch (IOException e) {
            Trace.warn("MW::addRooms failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean deleteRooms(String location) {
        Trace.info("MW::deleteRooms(" + location + ")");
        try {
            return roomRM.sendBool("deleteRooms", location);
        } catch (IOException e) {
            Trace.warn("MW::deleteRooms failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public int queryRooms(String location) {
        Trace.info("MW::queryRooms(" + location + ")");
        try {
            return roomRM.sendInt("queryRooms", location);
        } catch (IOException e) {
            Trace.warn("MW::queryRooms failed: " + e.getMessage());
            return -1;
        }
    }

    @Override
    public int queryRoomsPrice(String location) {
        Trace.info("MW::queryRoomsPrice(" + location + ")");
        try {
            return roomRM.sendInt("queryRoomsPrice", location);
        } catch (IOException e) {
            Trace.warn("MW::queryRoomsPrice failed: " + e.getMessage());
            return -1;
        }
    }

    @Override
    public int newCustomer() {
        int cid = Integer.parseInt(
                String.valueOf(Calendar.getInstance().get(Calendar.MILLISECOND)) +
                        String.valueOf(Math.round(Math.random() * 100 + 1))
        );
        while (customers.containsKey(cid)) {
            cid = rng.nextInt(Integer.MAX_VALUE);
        }
        customers.put(cid, new Customer(cid));
        Trace.info("MW::newCustomer() -> " + cid);
        return cid;
    }

    @Override
    public boolean newCustomer(int customerID) {
        if (customers.putIfAbsent(customerID, new Customer(customerID)) == null) {
            Trace.info("MW::newCustomer(" + customerID + ") created");
            return true;
        }
        Trace.info("MW::newCustomer(" + customerID + ") already exists");
        return false;
    }

    @Override
    public boolean deleteCustomer(int customerID) {
        Trace.info("MW::deleteCustomer(" + customerID + ") called");
        Customer c = customers.get(customerID);
        if (c == null) {
            Trace.warn("MW::deleteCustomer(" + customerID + ") failed -- customer does not exist");
            return false;
        }

        synchronized (c) {
            RMHashMap reservations = c.getReservations();
            for (String reservedKey : reservations.keySet()) {
                ReservedItem r = c.getReservedItem(reservedKey);
                String key   = r.getKey();   // e.g., "flight-123", "car-YYZ", "room-YYZ"
                int count    = r.getCount();

                try {
                    if (key.startsWith("flight-")) {
                        flightRM.sendBool("removeReservation", customerID, key, count);
                    } else if (key.startsWith("car-")) {
                        carRM.sendBool("removeReservation", customerID, key, count);
                    } else if (key.startsWith("room-")) {
                        roomRM.sendBool("removeReservation", customerID, key, count);
                    } else {
                        Trace.warn("MW::deleteCustomer unknown key type: " + key);
                    }
                } catch (IOException e) {
                    Trace.warn("MW::deleteCustomer removeReservation failed for " + key + ": " + e.getMessage());
                }
            }

            customers.remove(customerID);
        }

        Trace.info("MW::deleteCustomer(" + customerID + ") succeeded");
        return true;
    }


    @Override
    public String queryCustomerInfo(int customerID) {
        Trace.info("MW::queryCustomerInfo(" + customerID + ")");
        Customer c = customers.get(customerID);
        if (c == null) {
            return "";
        }
        synchronized (c) {
            return c.getBill();
        }
    }

    @Override
    public boolean reserveFlight(int customerID, int flightNumber) {
        Trace.info("MW::reserveFlight(" + customerID + ", " + flightNumber + ")");
        Customer c = customers.get(customerID);
        if (c == null) {
            Trace.warn("MW::reserveFlight failed -- customer doesn't exist");
            return false;
        }

        int price;
        try {
            price = flightRM.sendInt("queryFlightPrice", flightNumber);
            if (price <= 0) {
                Trace.warn("MW::reserveFlight failed -- flight doesn't exist");
                return false;
            }
            boolean ok = flightRM.sendBool("reserveFlight", customerID, flightNumber);
            if (!ok) {
                Trace.warn("MW::reserveFlight failed -- RM refused reservation");
                return false;
            }
        } catch (IOException e) {
            Trace.warn("MW::reserveFlight failed: " + e.getMessage());
            return false;
        }

        synchronized (c) {
            String key = Flight.getKey(flightNumber);
            c.reserve(key, String.valueOf(flightNumber), price);
        }
        return true;
    }

    @Override
    public boolean reserveCar(int customerID, String location) {
        Trace.info("MW::reserveCar(" + customerID + ", " + location + ")");
        Customer c = customers.get(customerID);
        if (c == null) {
            Trace.warn("MW::reserveCar failed -- customer doesn't exist");
            return false;
        }

        int price;
        try {
            price = carRM.sendInt("queryCarsPrice", location);
            if (price <= 0) {
                Trace.warn("MW::reserveCar failed -- location doesn't exist");
                return false;
            }
            boolean ok = carRM.sendBool("reserveCar", customerID, location);
            if (!ok) {
                Trace.warn("MW::reserveCar failed -- RM refused reservation");
                return false;
            }
        } catch (IOException e) {
            Trace.warn("MW::reserveCar failed: " + e.getMessage());
            return false;
        }

        synchronized (c) {
            String key = Car.getKey(location);
            c.reserve(key, location, price);
        }
        return true;
    }

    @Override
    public boolean reserveRoom(int customerID, String location) {
        Trace.info("MW::reserveRoom(" + customerID + ", " + location + ")");
        Customer c = customers.get(customerID);
        if (c == null) {
            Trace.warn("MW::reserveRoom failed -- customer doesn't exist");
            return false;
        }

        int price;
        try {
            price = roomRM.sendInt("queryRoomsPrice", location);
            if (price <= 0) {
                Trace.warn("MW::reserveRoom failed -- location doesn't exist");
                return false;
            }
            boolean ok = roomRM.sendBool("reserveRoom", customerID, location);
            if (!ok) {
                Trace.warn("MW::reserveRoom failed -- RM refused reservation");
                return false;
            }
        } catch (IOException e) {
            Trace.warn("MW::reserveRoom failed: " + e.getMessage());
            return false;
        }

        synchronized (c) {
            String key = Room.getKey(location);
            c.reserve(key, location, price);
        }
        return true;
    }

    @Override
    public boolean bundle(int customerID, Vector<String> flightNumbers, String location, boolean car, boolean room) {
        Trace.info("MW::bundle(" + customerID + ", flights=" + flightNumbers + ", loc=" + location + ", car=" + car + ", room=" + room + ")");
        Customer c = customers.get(customerID);
        if (c == null) {
            Trace.warn("MW::bundle failed -- customer doesn't exist");
            return false;
        }

        List<Integer> reservedFlights = new ArrayList<>();
        boolean reservedCar = false;
        boolean reservedRoom = false;

        Map<Integer, Integer> flightPrices = new LinkedHashMap<>();
        int carPrice = -1, roomPrice = -1;

        try {
            for (String fnStr : flightNumbers) {
                int fn = Integer.parseInt(fnStr);
                int price = flightRM.sendInt("queryFlightPrice", fn);
                if (price <= 0) {
                    Trace.warn("MW::bundle failed -- flight " + fn + " unavailable");
                    return false;
                }
                flightPrices.put(fn, price);
            }

            if (car) {
                carPrice = carRM.sendInt("queryCarsPrice", location);
                if (carPrice <= 0) {
                    Trace.warn("MW::bundle failed -- car at " + location + " unavailable");
                    return false;
                }
            }

            if (room) {
                roomPrice = roomRM.sendInt("queryRoomsPrice", location);
                if (roomPrice <= 0) {
                    Trace.warn("MW::bundle failed -- room at " + location + " unavailable");
                    return false;
                }
            }

            for (Integer fn : flightPrices.keySet()) {
                if (!flightRM.sendBool("reserveFlight", customerID, fn)) {
                    Trace.warn("MW::bundle failed -- reserveFlight failed for " + fn);
                    rollbackFlights(reservedFlights, customerID);
                    return false;
                }
                reservedFlights.add(fn);
            }

            if (car) {
                if (!carRM.sendBool("reserveCar", customerID, location)) {
                    Trace.warn("MW::bundle failed -- reserveCar failed");
                    rollbackFlights(reservedFlights, customerID);
                    return false;
                }
                reservedCar = true;
            }

            if (room) {
                if (!roomRM.sendBool("reserveRoom", customerID, location)) {
                    Trace.warn("MW::bundle failed -- reserveRoom failed");
                    if (reservedCar) releaseCar(location, customerID);
                    rollbackFlights(reservedFlights, customerID);
                    return false;
                }
                reservedRoom = true;
            }

        } catch (IOException e) {
            Trace.warn("MW::bundle comms failed: " + e.getMessage());
            if (reservedRoom) releaseRoom(location, customerID);
            if (reservedCar)  releaseCar(location, customerID);
            rollbackFlights(reservedFlights, customerID);
            return false;
        } catch (NumberFormatException ne) {
            Trace.warn("MW::bundle parse flight number failed: " + ne.getMessage());
            return false;
        }

        // Update local customer state after all succeeded
        synchronized (c) {
            for (Integer fn : flightPrices.keySet()) {
                c.reserve(Flight.getKey(fn), String.valueOf(fn), flightPrices.get(fn));
            }
            if (reservedCar)  c.reserve(Car.getKey(location),  location,  carPrice);
            if (reservedRoom) c.reserve(Room.getKey(location), location,  roomPrice);
        }
        return true;
    }

    private void rollbackFlights(List<Integer> reservedFlights, int customerID) {
        for (Integer fn : reservedFlights) {
            try {
                String key = Flight.getKey(fn);
                flightRM.sendBool("removeReservation", customerID, key, 1);
            } catch (IOException ignored) {}
        }
    }

    private void releaseCar(String location, int customerID) {
        try {
            String key = Car.getKey(location);
            carRM.sendBool("removeReservation", customerID, key, 1);
        } catch (IOException ignored) {}
    }

    private void releaseRoom(String location, int customerID) {
        try {
            String key = Room.getKey(location);
            roomRM.sendBool("removeReservation", customerID, key, 1);
        } catch (IOException ignored) {}
    }

    @Override
    public String getName() {
        return m_name;
    }
}
