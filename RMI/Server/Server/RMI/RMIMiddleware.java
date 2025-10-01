package Server.RMI;

import Server.Interface.*;
import Server.Common.*;
import Server.Middleware.MiddleWare;

import java.rmi.NotBoundException;
import java.util.*;

import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class RMIMiddleware extends MiddleWare
{
    private static String s_serverName = "Middleware";
    private static String s_rmiPrefix = "group_10_";

    public static void main(String args[])
    {
        if (args.length != 3) {
            System.err.println("Usage: java Server.Middleware.RMIMiddleware <flightRM_host> <carRM_host> <roomRM_host>");
            System.exit(1);
        }

        String flightHost = args[0];
        String carHost = args[1];
        String roomHost = args[2];

        try {
            RMIMiddleware middleware = new RMIMiddleware(s_serverName);
            middleware.connectToResourceManagers(flightHost, carHost, roomHost);

            IResourceManager resourceManager = (IResourceManager)UnicastRemoteObject.exportObject(middleware, 0);

            // Bind to existing registry
            Registry l_registry = LocateRegistry.getRegistry(3010);
            final Registry registry = l_registry;
            registry.rebind(s_rmiPrefix + s_serverName, resourceManager);

            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    try {
                        registry.unbind(s_rmiPrefix + s_serverName);
                        System.out.println("'" + s_serverName + "' middleware unbound");
                    }
                    catch(Exception e) {
                        System.err.println((char)27 + "[31;1mServer exception: " + (char)27 + "[0mUncaught exception");
                        e.printStackTrace();
                    }
                }
            });
            System.out.println("'" + s_serverName + "' middleware server ready and bound to '" + s_rmiPrefix + s_serverName + "'");
            System.out.println("Connected to:");
            System.out.println("  - Flights RM: " + flightHost);
            System.out.println("  - Cars RM: " + carHost);
            System.out.println("  - Rooms RM: " + roomHost);
        }
        catch (Exception e) {
            System.err.println((char)27 + "[31;1mServer exception: " + (char)27 + "[0mUncaught exception");
            e.printStackTrace();
            System.exit(1);
        }
    }

    public RMIMiddleware(String name)
    {
        super(name);
    }
}