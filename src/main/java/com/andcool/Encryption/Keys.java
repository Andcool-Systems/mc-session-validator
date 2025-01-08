package com.andcool.Encryption;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class Keys {
    public static SecretKey generateRandomSecret() {
        try {
            KeyGenerator secretKeyGen = KeyGenerator.getInstance("AES");
            secretKeyGen.init(128);
            return secretKeyGen.generateKey();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
