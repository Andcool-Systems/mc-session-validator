package com.andcool.Mojang;

import com.andcool.Main;
import com.andcool.SESSION;
import org.json.JSONObject;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.PublicKey;


public class Join {
    public static void join(String server_id, PublicKey publicKey) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        digest.update(server_id.getBytes());
        assert Main.sharedSecret != null;
        digest.update(Main.sharedSecret.getEncoded());
        digest.update(publicKey.getEncoded());
        String hash = new BigInteger(digest.digest()).toString(16);

        System.out.println("Client hash: " + hash);

        JSONObject body = new JSONObject();

        body.put("accessToken", SESSION.accessToken);
        body.put("selectedProfile", Main.UUID.replaceAll("-", ""));
        body.put("serverId", hash);

        String BASE_URL = "https://sessionserver.mojang.com/session/minecraft/join";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .header("Content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Mojang API responded with code: " + response.statusCode());
    }
}
