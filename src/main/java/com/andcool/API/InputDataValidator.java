package com.andcool.API;

import org.json.JSONException;
import org.json.JSONObject;

public class InputDataValidator {
    public static JSONObject validate(String request) throws HTTPException {
        try {
            JSONObject obj = new JSONObject(request);

            validateString(obj, "nickname");
            validateString(obj, "UUID");
            validateString(obj, "accessToken");

            validateJSON(obj, "server");

            JSONObject server = obj.getJSONObject("server");
            validateString(server, "ip");
            validateInt(server, "port");
            validateInt(server, "protocolVersion");

            return obj;
        } catch (JSONException e) {
            throw new HTTPException("Couldn't parse JSON body", 422);
        }
    }

    private static void validateString(JSONObject obj, String key) throws HTTPException {
        try {
            obj.getString(key);
        } catch (JSONException e) {
            throw new HTTPException("Field with key `" + key + "` not found", 400);
        }
    }

    private static void validateInt(JSONObject obj, String key) throws HTTPException {
        try {
            obj.getInt(key);
        } catch (JSONException e) {
            throw new HTTPException("Field with key `" + key + "` not found or value cannot converted to integer", 400);
        }
    }

    private static void validateJSON(JSONObject obj, String key) throws HTTPException {
        try {
            obj.getJSONObject(key);
        } catch (JSONException e) {
            throw new HTTPException("Field with key `" + key + "` not found or value is not valid JSON", 400);
        }
    }
}
