package com.andcool.session;

public class ServerCause {
    public static String getCause(String disconnectMessage) {
        String message = disconnectMessage.toLowerCase();
        if (message.contains("whitelist")) {
            return "Whitelist";
        }
        if (message.contains("minecraft.net")
                || message.contains("not authenticated with")
                || message.contains("invalid session")
                || message.contains("bad login")) {
            return "Mojang session";
        }
        if (message.contains("internal server")) {
            return "Server error";
        }
        if (message.contains("outdated")) {
            return "Server version";
        }
        if (message.contains("banned")) {
            return "Client banned";
        }
        if (message.contains("time")
                || message.contains("out")) {
            return "Timed out";
        }
        return "Disconnect";
    }

}
