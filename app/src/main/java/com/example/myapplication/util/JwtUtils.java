package com.example.myapplication.util;

import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * Decodifica localmente el payload de un JWT para saber si ya expiro, sin
 * necesidad de una llamada de red. El backend firma el token con un claim
 * "exp" estandar (ver JwtUtils.generateToken en el backend) y lo rechaza
 * exactamente con esa misma fecha de expiracion, asi que este chequeo local
 * equivale al que haria el servidor.
 */
public final class JwtUtils {

    private JwtUtils() {
    }

    /** true si el token esta vencido o no se pudo leer su expiracion (token invalido/corrupto). */
    public static boolean isExpired(String token) {
        Long expirationSeconds = extractExpirationSeconds(token);
        if (expirationSeconds == null) {
            return true;
        }
        return System.currentTimeMillis() >= expirationSeconds * 1000L;
    }

    private static Long extractExpirationSeconds(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            String payloadEncoded = parts[1];
            int paddingNeeded = (4 - payloadEncoded.length() % 4) % 4;
            StringBuilder padded = new StringBuilder(payloadEncoded);
            for (int i = 0; i < paddingNeeded; i++) {
                padded.append('=');
            }

            byte[] payloadBytes = Base64.decode(padded.toString(), Base64.URL_SAFE);
            JSONObject payload = new JSONObject(new String(payloadBytes, StandardCharsets.UTF_8));
            if (!payload.has("exp")) {
                return null;
            }
            return payload.getLong("exp");
        } catch (Exception e) {
            return null;
        }
    }
}
