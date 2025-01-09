package com.andcool.Mojang;

import com.andcool.API.HTTPException;
import com.andcool.SillyLogger.Level;
import com.andcool.SillyLogger.SillyLogger;
import org.json.JSONObject;

import javax.crypto.SecretKey;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.PublicKey;


public class Join {
    public static final SillyLogger logger = new SillyLogger("", true, Level.DEBUG);

    public static void join(String server_id,
                            PublicKey publicKey,
                            String uuid,
                            SecretKey sharedSecret,
                            String accessToken) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        digest.update(server_id.getBytes());
        assert sharedSecret != null;
        digest.update(sharedSecret.getEncoded());
        digest.update(publicKey.getEncoded());
        String hash = new BigInteger(digest.digest()).toString(16);

        logger.log(Level.DEBUG, "Client hash: " + hash);

        JSONObject body = new JSONObject();

        body.put("accessToken", accessToken);
        body.put("selectedProfile", uuid.replaceAll("-", ""));
        body.put("serverId", hash);

        logger.log(Level.DEBUG, "Trying to authenticate with Mojang API: " + hash);
        String BASE_URL = "https://sessionserver.mojang.com/session/minecraft/join";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .header("Content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400)
            throw new HTTPException("Mojang API error", response.statusCode());
        logger.log(Level.DEBUG, "Mojang API responded with code: " + response.statusCode());
    }
}
