package com.andcool.API;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.andcool.Main;
import com.andcool.MinecraftClient;
import com.andcool.SillyLogger.Level;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;

import static java.lang.String.format;

public class APIHandler implements HttpHandler {
    private final Pattern pattern = Pattern.compile("/connect");

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            Matcher matcher = pattern.matcher(path);

            if (!matcher.matches())
                throw new HTTPException("Not found", 404);

            if (!exchange.getRequestMethod().equalsIgnoreCase("post"))
                throw new HTTPException("Method not allowed", 405);

            String body = new String(exchange.getRequestBody().readAllBytes());

            JSONObject requestBody;
            requestBody = InputDataValidator.validate(body);

            String nickname = requestBody.getString("nickname");
            String UUID = requestBody.getString("UUID");
            String accessToken = requestBody.getString("accessToken");

            JSONObject server = requestBody.getJSONObject("server");
            String ip = server.getString("ip");
            int port = server.getInt("port");
            int protocolVersion = server.getInt("protocolVersion");

            Main.logger.log(Level.INFO, format("Started connection to %s for %s", ip, nickname));

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<JSONObject> future = executor.submit(new MinecraftClient(
                    ip,
                    port,
                    nickname,
                    UUID,
                    protocolVersion,
                    accessToken
            ));
            JSONObject response = future.get();

            Main.logger.log(Level.INFO, "Successfully resolved request for " + nickname);
            send(exchange, response.toString(), 200);
        } catch (Exception e) {
            Exception mustLogged = e;
            if (e instanceof HTTPException cause) {
                send(exchange, cause.getMessage(), cause.statusCode);
            } else if (e instanceof ExecutionException cause) {
                Throwable connectionException = cause.getCause();
                if (connectionException instanceof HTTPException httpException) {
                    mustLogged = httpException;
                    send(exchange, httpException.getMessage(), httpException.statusCode);
                } else {
                    send(exchange, "{\"message\": \"Internal server exception\"}", 500);
                }
            } else {
                send(exchange, "{\"message\": \"Internal server exception\"}", 500);
            }

            Main.logger.log(Level.ERROR, mustLogged, true);
        }
    }

    private void send(HttpExchange exchange, String response, int status_code) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status_code, response.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
}