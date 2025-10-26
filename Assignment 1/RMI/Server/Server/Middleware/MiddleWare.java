package Server.Middleware;

import Server.Interface.*;
import Server.Common.*;

import java.rmi.RemoteException;

import java.rmi.NotBoundException;

import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.util.Vector;

public class MiddleWare extends ResourceManager {

    // Connections to the three ResourceManagers
    protected IResourceManager flightRM = null;
    protected IResourceManager carRM = null;
    protected IResourceManager roomRM = null;


    private static String serverName = "Middleware";
    private static String rmiPrefix = "group_10_"; // TODO: Change to your group number
    private static int rmiPort = 3010;

    public MiddleWare(String name) {
        super(name);
    }
     protected void connectServer(String type, String server, int port, String name) {
         try {
             boolean first = true;
             while (true) {
                 try {
                     Registry registry = LocateRegistry.getRegistry(server, port);

                     switch(type) {
                         case "Flight":
                             flightRM = (IResourceManager)registry.lookup(name);
                             System.out.println("Connected to Flights ResourceManager at " + server);
                             break;
                         case "Car":
                             carRM = (IResourceManager)registry.lookup(name);
                             System.out.println("Connected to Cars ResourceManager at " + server);
                             break;
                         case "Room":
                             roomRM = (IResourceManager)registry.lookup(name);
                             System.out.println("Connected to Rooms ResourceManager at " + server);
                             break;
                         default:
                             System.err.println("Unknown server type: " + type);
                             return;
                     }
                     break;
                 }
                 catch (NotBoundException|RemoteException e) {
                     if (first) {
                         System.out.println("Waiting for '" + name + "' server [" + server + ":" + port + "/" + name + "]");
                         first = false;
                     }
                 }
                 Thread.sleep(500);
             }
         }
         catch (Exception e) {
             System.err.println("Server connection exception: " + e.getMessage());
             e.printStackTrace();
             System.exit(1);
         }
     }
     public void connectToResourceManagers(String flightHost, String carHost, String roomHost)
             throws RemoteException, NotBoundException {

         Trace.info("MW::connectToResourceManagers() called");

         // Connect to Flight ResourceManager
         connectServer("Flight", flightHost, 3010, "group_10_Flights");

         // Connect to Car ResourceManager
         connectServer("Car", carHost, 3010, "group_10_Cars");

         // Connect to Room ResourceManager
         connectServer("Room", roomHost, 3010, "group_10_Rooms");
     }
    public String getName() throws RemoteException {
        return m_name;
    }
     @Override
     public boolean addFlight(int flightNum, int flightSeats, int flightPrice) throws RemoteException {
         return flightRM.addFlight(flightNum, flightSeats, flightPrice);
     }

     @Override
     public boolean deleteFlight(int flightNum) throws RemoteException {
         return flightRM.deleteFlight(flightNum);
     }

     @Override
     public int queryFlight(int flightNum) throws RemoteException {
         return flightRM.queryFlight(flightNum);
     }

     @Override
     public int queryFlightPrice(int flightNum) throws RemoteException {
         return flightRM.queryFlightPrice(flightNum);
     }

     @Override
     public boolean addCars(String location, int numCars, int price) throws RemoteException {
         return carRM.addCars(location, numCars, price);
     }

     @Override
     public boolean deleteCars(String location) throws RemoteException {
         return carRM.deleteCars(location);
     }

     @Override
     public int queryCars(String location) throws RemoteException {
         return carRM.queryCars(location);
     }

     @Override
     public int queryCarsPrice(String location) throws RemoteException {
         return carRM.queryCarsPrice(location);
     }

     @Override
     public boolean addRooms(String location, int numRooms, int price) throws RemoteException {
         return roomRM.addRooms(location, numRooms, price);
     }

     @Override
     public boolean deleteRooms(String location) throws RemoteException {
         return roomRM.deleteRooms(location);
     }

     @Override
     public int queryRooms(String location) throws RemoteException {
         return roomRM.queryRooms(location);
     }

     @Override
     public int queryRoomsPrice(String location) throws RemoteException {
         return roomRM.queryRoomsPrice(location);
     }

     @Override
     public boolean reserveFlight(int customerID, int flightNum) throws RemoteException {
         Trace.info("MW::reserveFlight(" + customerID + ", " + flightNum + ") called");

         // Check if customer exists locally
         Customer customer = (Customer)readData(Customer.getKey(customerID));
         if (customer == null) {
             Trace.warn("MW::reserveFlight(" + customerID + ", " + flightNum + ") failed--customer doesn't exist");
             return false;
         }

         // Get price from flight RM
         int flightPrice = flightRM.queryFlightPrice(flightNum);
         if (flightPrice <= 0) {
             Trace.warn("MW::reserveFlight(" + customerID + ", " + flightNum + ") failed--flight doesn't exist");
             return false;
         }

         // Delegate reservation to flight RM
         if (flightRM.reserveFlight(customerID, flightNum)) {
             // COPY: Update customer's reservation list locally
             String key = Flight.getKey(flightNum);
             customer.reserve(key, String.valueOf(flightNum), flightPrice);
             writeData(customer.getKey(), customer);

             Trace.info("MW::reserveFlight(" + customerID + ", " + flightNum + ") succeeded");
             return true;
         }

         return false;
     }

     @Override
     public boolean reserveCar(int customerID, String location) throws RemoteException {
         Trace.info("MW::reserveCar(" + customerID + ", " + location + ") called");


         Customer customer = (Customer)readData(Customer.getKey(customerID));
         if (customer == null) {
             Trace.warn("MW::reserveCar(" + customerID + ", " + location + ") failed--customer doesn't exist");
             return false;
         }


         int carPrice = carRM.queryCarsPrice(location);
         if (carPrice <= 0) {
             Trace.warn("MW::reserveCar(" + customerID + ", " + location + ") failed--location doesn't exist");
             return false;
         }

         if (carRM.reserveCar(customerID, location)) {
             // Update customer's reservation list locally
             String key = Car.getKey(location);
             customer.reserve(key, location, carPrice);
             writeData(customer.getKey(), customer);

             Trace.info("MW::reserveCar(" + customerID + ", " + location + ") succeeded");
             return true;
         }

         return false;
     }

     @Override
     public boolean reserveRoom(int customerID, String location) throws RemoteException {
         Trace.info("MW::reserveRoom(" + customerID + ", " + location + ") called");


         Customer customer = (Customer)readData(Customer.getKey(customerID));
         if (customer == null) {
             Trace.warn("MW::reserveRoom(" + customerID + ", " + location + ") failed--customer doesn't exist");
             return false;
         }


         int roomPrice = roomRM.queryRoomsPrice(location);
         if (roomPrice <= 0) {
             Trace.warn("MW::reserveRoom(" + customerID + ", " + location + ") failed--location doesn't exist");
             return false;
         }


         if (roomRM.reserveRoom(customerID, location)) {
             // COPY: Update customer's reservation list locally
             String key = Room.getKey(location);
             customer.reserve(key, location, roomPrice);
             writeData(customer.getKey(), customer);

             Trace.info("MW::reserveRoom(" + customerID + ", " + location + ") succeeded");
             return true;
         }

         return false;
     }

    @Override
    public boolean deleteCustomer(int customerID) throws RemoteException {
        Trace.info("MW::deleteCustomer(" + customerID + ") called");

        Customer customer = (Customer)readData(Customer.getKey(customerID));
        if (customer == null) {
            Trace.warn("MW::deleteCustomer(" + customerID + ") failed--customer doesn't exist");
            return false;
        }
        RMHashMap reservations = customer.getReservations();
        for (String reservedKey : reservations.keySet()) {
            ReservedItem reservedItem = customer.getReservedItem(reservedKey);

            try {
                if (reservedKey.startsWith("flight-")) {
                    flightRM.removeReservation(customerID, reservedItem.getKey(), reservedItem.getCount());
                }
                else if (reservedKey.startsWith("car-")) {
                    carRM.removeReservation(customerID, reservedItem.getKey(), reservedItem.getCount());
                }
                else if (reservedKey.startsWith("room-")) {
                    roomRM.removeReservation(customerID, reservedItem.getKey(), reservedItem.getCount());
                }
            } catch (Exception e) {
                Trace.warn("MW::deleteCustomer(" + customerID + ") error removing reservation: " + e.getMessage());
            }
        }

        removeData(customer.getKey());
        Trace.info("MW::deleteCustomer(" + customerID + ") succeeded");
        return true;
    }
    @Override
    public boolean bundle(int customerID, Vector<String> flightNumbers, String location, boolean car, boolean room) throws RemoteException {
        Trace.info("MW::bundle(" + customerID + ", flights=" + flightNumbers + ", location=" + location + ", car=" + car + ", room=" + room + ") called");


        Customer customer = (Customer)readData(Customer.getKey(customerID));
        if (customer == null) {
            Trace.warn("MW::bundle(" + customerID + ") failed--customer doesn't exist");
            return false;
        }

        for (String flightNumStr : flightNumbers) {
            try {
                int flightNum = Integer.parseInt(flightNumStr);
                int available = flightRM.queryFlight(flightNum);
                int price = flightRM.queryFlightPrice(flightNum);

                if (available <= 0 || price <= 0) {
                    Trace.warn("MW::bundle(" + customerID + ") failed--flight " + flightNum + " not available");
                    return false;
                }
            } catch (NumberFormatException e) {
                Trace.warn("MW::bundle(" + customerID + ") failed--invalid flight number: " + flightNumStr);
                return false;
            }
        }

        if (car) {
            int available = carRM.queryCars(location);
            int price = carRM.queryCarsPrice(location);
            if (available <= 0 || price <= 0) {
                Trace.warn("MW::bundle(" + customerID + ") failed--no cars available at " + location);
                return false;
            }
        }

        if (room) {
            int available = roomRM.queryRooms(location);
            int price = roomRM.queryRoomsPrice(location);
            if (available <= 0 || price <= 0) {
                Trace.warn("MW::bundle(" + customerID + ") failed--no rooms available at " + location);
                return false;
            }
        }


        for (String flightNumStr : flightNumbers) {
            int flightNum = Integer.parseInt(flightNumStr);
            if (!reserveFlight(customerID, flightNum)) {
                Trace.warn("MW::bundle(" + customerID + ") failed--could not reserve flight " + flightNum);
                return false;
            }
        }

        // Reserve car
        if (car) {
            if (!reserveCar(customerID, location)) {
                Trace.warn("MW::bundle(" + customerID + ") failed--could not reserve car at " + location);
                return false;
            }
        }

        // Reserve room
        if (room) {
            if (!reserveRoom(customerID, location)) {
                Trace.warn("MW::bundle(" + customerID + ") failed--could not reserve room at " + location);
                return false;
            }
        }

        Trace.info("MW::bundle(" + customerID + ") succeeded");
        return true;
    }
}