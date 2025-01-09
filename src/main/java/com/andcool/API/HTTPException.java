package com.andcool.API;

import org.json.JSONObject;

public class HTTPException extends Exception {
    public int statusCode = 400;

    public HTTPException(String message) {
        super(message);
    }

    public HTTPException(String message, int code) {
        super("{\"message\": \"" + message + "\"}");
        statusCode = code;
    }

    public HTTPException(JSONObject message, int code) {
        super(message.toString());
        statusCode = code;
    }
}
