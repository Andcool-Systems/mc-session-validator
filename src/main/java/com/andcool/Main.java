package com.andcool;

import com.andcool.API.APIHandler;
import com.andcool.SillyLogger.Level;
import com.andcool.SillyLogger.SillyLogger;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

public class Main {
    public static final SillyLogger logger = new SillyLogger("Main Thread", true, Level.DEBUG);

    public static void main(String[] args) throws IOException {
        logger.log(Level.INFO, "Starting API");
        HttpServer server = HttpServer.create(new InetSocketAddress(8008), 0);
        server.createContext("/connect", new APIHandler());
        server.start();
    }
}
