package Client;

import java.util.*;
import java.io.*;

public class Client {

    private final TCPClient tcp;

    public Client(TCPClient tcp) {
        this.tcp = tcp;
    }

    public void start() {
        System.out.println();
        System.out.println("Type \"help\" for list of supported commands");

        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            String command = "";
            Vector<String> arguments;

            try {
                System.out.print((char)27 + "[32;1m\n>] " + (char)27 + "[0m");
                command = stdin.readLine();
                if (command == null) {
                    System.out.println("Input closed");
                    return;
                }
                command = command.trim();
            } catch (IOException io) {
                System.err.println((char)27 + "[31;1mClient exception: " + (char)27 + "[0m" + io.getLocalizedMessage());
                io.printStackTrace();
                return;
            }

            try {
                arguments = parse(command);
                Command cmd = Command.fromString(arguments.elementAt(0));
                execute(cmd, arguments);
            } catch (IllegalArgumentException e) {
                System.err.println((char)27 + "[31;1mCommand exception: " + (char)27 + "[0m" + e.getLocalizedMessage());
            } catch (IOException e) {
                System.err.println((char)27 + "[31;1mCommand exception: " + (char)27 + "[0mConnection error: " + e.getMessage());
            } catch (Exception e) {
                System.err.println((char)27 + "[31;1mCommand exception: " + (char)27 + "[0mUncaught exception");
                e.printStackTrace();
            }
        }
    }

    private void execute(Command cmd, Vector<String> arguments) throws IOException, NumberFormatException {
        switch (cmd) {
            case Help: {
                if (arguments.size() == 1) {
                    System.out.println(Command.description());
                } else if (arguments.size() == 2) {
                    Command l_cmd = Command.fromString(arguments.elementAt(1));
                    System.out.println(l_cmd.toString());
                } else {
                    System.err.println((char)27 + "[31;1mCommand exception: " + (char)27 + "[0mImproper use of help command. Type \"help\" or \"help,<CommandName>\"");
                }
                break;
            }

            case AddFlight: {
                checkArgumentsCount(4, arguments.size());

                System.out.println("Adding a new flight ");
                System.out.println("-Flight Number: " + arguments.elementAt(1));
                System.out.println("-Flight Seats: " + arguments.elementAt(2));
                System.out.println("-Flight Price: " + arguments.elementAt(3));

                int flightNum = toInt(arguments.elementAt(1));
                int flightSeats = toInt(arguments.elementAt(2));
                int flightPrice = toInt(arguments.elementAt(3));

                if (tcp.addFlight(flightNum, flightSeats, flightPrice)) {
                    System.out.println("Flight added");
                } else {
                    System.out.println("Flight could not be added");
                }
                break;
            }

            case AddCars: {
                checkArgumentsCount(4, arguments.size());

                System.out.println("Adding new cars");
                System.out.println("-Car Location: " + arguments.elementAt(1));
                System.out.println("-Number of Cars: " + arguments.elementAt(2));
                System.out.println("-Car Price: " + arguments.elementAt(3));

                String location = arguments.elementAt(1);
                int numCars = toInt(arguments.elementAt(2));
                int price = toInt(arguments.elementAt(3));

                if (tcp.addCars(location, numCars, price)) {
                    System.out.println("Cars added");
                } else {
                    System.out.println("Cars could not be added");
                }
                break;
            }

            case AddRooms: {
                checkArgumentsCount(4, arguments.size());

                System.out.println("Adding new rooms");
                System.out.println("-Room Location: " + arguments.elementAt(1));
                System.out.println("-Number of Rooms: " + arguments.elementAt(2));
                System.out.println("-Room Price: " + arguments.elementAt(3));

                String location = arguments.elementAt(1);
                int numRooms = toInt(arguments.elementAt(2));
                int price = toInt(arguments.elementAt(3));

                if (tcp.addRooms(location, numRooms, price)) {
                    System.out.println("Rooms added");
                } else {
                    System.out.println("Rooms could not be added");
                }
                break;
            }

            case AddCustomer: {
                checkArgumentsCount(1, arguments.size());

                System.out.println("Adding a new customer:=");

                int customer = tcp.newCustomer();

                System.out.println("Add customer ID: " + customer);
                break;
            }

            case AddCustomerID: {
                checkArgumentsCount(2, arguments.size());

                System.out.println("Adding a new customer");
                System.out.println("-Customer ID: " + arguments.elementAt(1));

                int customerID = toInt(arguments.elementAt(1));

                if (tcp.newCustomer(customerID)) {
                    System.out.println("Add customer ID: " + customerID);
                } else {
                    System.out.println("Customer could not be added");
                }
                break;
            }

            case DeleteFlight: {
                checkArgumentsCount(2, arguments.size());

                System.out.println("Deleting a flight");
                System.out.println("-Flight Number: " + arguments.elementAt(1));

                int flightNum = toInt(arguments.elementAt(1));

                if (tcp.deleteFlight(flightNum)) {
                    System.out.println("Flight Deleted");
                } else {
                    System.out.println("Flight could not be deleted");
                }
                break;
            }

            case DeleteCars: {
                checkArgumentsCount(2, arguments.size());

                System.out.println("Deleting all cars at a particular location");
                System.out.println("-Car Location: " + arguments.elementAt(1));

                String location = arguments.elementAt(1);

                if (tcp.deleteCars(location)) {
                    System.out.println("Cars Deleted");
                } else {
                    System.out.println("Cars could not be deleted");
                }
                break;
            }

            case DeleteRooms: {
                checkArgumentsCount(2, arguments.size());

                System.out.println("Deleting all rooms at a particular location");
                System.out.println("-Room Location: " + arguments.elementAt(1));

                String location = arguments.elementAt(1);

                if (tcp.deleteRooms(location)) {
                    System.out.println("Rooms Deleted");
                } else {
                    System.out.println("Rooms could not be deleted");
                }
                break;
            }

            case DeleteCustomer: {
                checkArgumentsCount(2, arguments.size());

                System.out.println("Deleting a customer from the database");
                System.out.println("-Customer ID: " + arguments.elementAt(1));

                int customerID = toInt(arguments.elementAt(1));

                if (tcp.deleteCustomer(customerID)) {
                    System.out.println("Customer Deleted");
                } else {
                    System.out.println("Customer could not be deleted");
                }
                break;
            }

            case QueryFlight: {
                checkArgumentsCount(2, arguments.size());

                System.out.println("Querying a flight");
                System.out.println("-Flight Number: " + arguments.elementAt(1));

                int flightNum = toInt(arguments.elementAt(1));

                int seats = tcp.queryFlight(flightNum);
                System.out.println("Number of seats available: " + seats);
                break;
            }

            case QueryCars: {
                checkArgumentsCount(2, arguments.size());

                System.out.println("Querying cars location");
                System.out.println("-Car Location: " + arguments.elementAt(1));

                String location = arguments.elementAt(1);

                int numCars = tcp.queryCars(location);
                System.out.println("Number of cars at this location: " + numCars);
                break;
            }

            case QueryRooms: {
                checkArgumentsCount(2, arguments.size());

                System.out.println("Querying rooms location");
                System.out.println("-Room Location: " + arguments.elementAt(1));

                String location = arguments.elementAt(1);

                int numRoom = tcp.queryRooms(location);
                System.out.println("Number of rooms at this location: " + numRoom);
                break;
            }

            case QueryCustomer: {
                checkArgumentsCount(2, arguments.size());

                System.out.println("Querying customer information");
                System.out.println("-Customer ID: " + arguments.elementAt(1));

                int customerID = toInt(arguments.elementAt(1));

                String bill = tcp.queryCustomerInfo(customerID);
                System.out.print(bill);
                break;
            }

            case QueryFlightPrice: {
                checkArgumentsCount(2, arguments.size());

                System.out.println("Querying a flight price");
                System.out.println("-Flight Number: " + arguments.elementAt(1));

                int flightNum = toInt(arguments.elementAt(1));

                int price = tcp.queryFlightPrice(flightNum);
                System.out.println("Price of a seat: " + price);
                break;
            }

            case QueryCarsPrice: {
                checkArgumentsCount(2, arguments.size());

                System.out.println("Querying cars price");
                System.out.println("-Car Location: " + arguments.elementAt(1));

                String location = arguments.elementAt(1);

                int price = tcp.queryCarsPrice(location);
                System.out.println("Price of cars at this location: " + price);
                break;
            }

            case QueryRoomsPrice: {
                checkArgumentsCount(2, arguments.size());

                System.out.println("Querying rooms price");
                System.out.println("-Room Location: " + arguments.elementAt(1));

                String location = arguments.elementAt(1);

                int price = tcp.queryRoomsPrice(location);
                System.out.println("Price of rooms at this location: " + price);
                break;
            }

            case ReserveFlight: {
                checkArgumentsCount(3, arguments.size());

                System.out.println("Reserving seat in a flight");
                System.out.println("-Customer ID: " + arguments.elementAt(1));
                System.out.println("-Flight Number: " + arguments.elementAt(2));

                int customerID = toInt(arguments.elementAt(1));
                int flightNum = toInt(arguments.elementAt(2));

                if (tcp.reserveFlight(customerID, flightNum)) {
                    System.out.println("Flight Reserved");
                } else {
                    System.out.println("Flight could not be reserved");
                }
                break;
            }

            case ReserveCar: {
                checkArgumentsCount(3, arguments.size());

                System.out.println("Reserving a car at a location");
                System.out.println("-Customer ID: " + arguments.elementAt(1));
                System.out.println("-Car Location: " + arguments.elementAt(2));

                int customerID = toInt(arguments.elementAt(1));
                String location = arguments.elementAt(2);

                if (tcp.reserveCar(customerID, location)) {
                    System.out.println("Car Reserved");
                } else {
                    System.out.println("Car could not be reserved");
                }
                break;
            }

            case ReserveRoom: {
                checkArgumentsCount(3, arguments.size());

                System.out.println("Reserving a room at a location");
                System.out.println("-Customer ID: " + arguments.elementAt(1));
                System.out.println("-Room Location: " + arguments.elementAt(2));

                int customerID = toInt(arguments.elementAt(1));
                String location = arguments.elementAt(2);

                if (tcp.reserveRoom(customerID, location)) {
                    System.out.println("Room Reserved");
                } else {
                    System.out.println("Room could not be reserved");
                }
                break;
            }

            case Bundle: {
                if (arguments.size() < 6) {
                    System.err.println((char)27 + "[31;1mCommand exception: " + (char)27 + "[0mBundle command expects at least 6 arguments. Type \"help\" or \"help,<CommandName>\"");
                    break;
                }

                System.out.println("Reserving a bundle");
                System.out.println("-Customer ID: " + arguments.elementAt(1));
                for (int i = 0; i < arguments.size() - 5; ++i) {
                    System.out.println("-Flight Number: " + arguments.elementAt(2 + i));
                }
                System.out.println("-Location for Car/Room: " + arguments.elementAt(arguments.size()-3));
                System.out.println("-Book Car: " + arguments.elementAt(arguments.size()-2));
                System.out.println("-Book Room: " + arguments.elementAt(arguments.size()-1));

                int customerID = toInt(arguments.elementAt(1));

                Vector<String> flights = new Vector<>();
                for (int i = 0; i < arguments.size() - 5; ++i) {
                    flights.addElement(arguments.elementAt(2 + i));
                }

                String location = arguments.elementAt(arguments.size()-3);
                boolean car = toBoolean(arguments.elementAt(arguments.size()-2));
                boolean room = toBoolean(arguments.elementAt(arguments.size()-1));

                boolean ok;
                try {
                    ok = tcp.bundle(customerID, flights, location, car, room);
                } catch (IOException ex) {
                    ok = false;
                }

                if (!ok) {
                    boolean any = true;
                    for (String f : flights) {
                        any &= tcp.reserveFlight(customerID, toInt(f));
                    }
                    if (car)  any &= tcp.reserveCar(customerID, location);
                    if (room) any &= tcp.reserveRoom(customerID, location);
                    ok = any;
                }

                if (ok) {
                    System.out.println("Bundle Reserved");
                } else {
                    System.out.println("Bundle could not be reserved");
                }
                break;
            }

            case Quit: {
                checkArgumentsCount(1, arguments.size());
                System.out.println("Quitting client");
                System.exit(0);
            }
        }
    }

    private static Vector<String> parse(String command) {
        Vector<String> arguments = new Vector<>();
        StringTokenizer tokenizer = new StringTokenizer(command, ",");
        while (tokenizer.hasMoreTokens()) {
            arguments.add(tokenizer.nextToken().trim());
        }
        return arguments;
    }

    private static void checkArgumentsCount(int expected, int actual) {
        if (expected != actual) {
            throw new IllegalArgumentException("Invalid number of arguments. Expected " + (expected - 1) + ", received " + (actual - 1) + ". Type \"help,<CommandName>\" to check usage of this command");
        }
    }

    private static int toInt(String string) {
        return Integer.parseInt(string);
    }

    private static boolean toBoolean(String string) {
        if (string == null || string.isEmpty()) return false;
        char c = Character.toLowerCase(string.trim().charAt(0));
        return c == 't' || c == 'y' || c == '1';
    }
}
