package com.boulangerie.service;

import java.util.HashMap;
import java.util.Map;

/**
 * Protection locale contre les tentatives répétées sur l'écran de connexion.
 * Cette sécurité est volontairement en mémoire pour éviter une migration SQL.
 */
public class LoginAttemptService {
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MS = 5 * 60 * 1000L;

    private static LoginAttemptService instance;

    private final Map<String, Integer> attemptsByLogin = new HashMap<>();
    private final Map<String, Long> lockedUntilByLogin = new HashMap<>();

    private LoginAttemptService() {}

    public static synchronized LoginAttemptService getInstance() {
        if (instance == null) {
            instance = new LoginAttemptService();
        }
        return instance;
    }

    public synchronized void verifierAutorisation(String login) {
        String key = normalize(login);
        long now = System.currentTimeMillis();
        Long lockedUntil = lockedUntilByLogin.get(key);

        if (lockedUntil == null) {
            return;
        }

        if (lockedUntil <= now) {
            lockedUntilByLogin.remove(key);
            attemptsByLogin.remove(key);
            return;
        }

        throw new IllegalArgumentException(
            "Trop de tentatives de connexion. Réessayez dans "
                + formatRemainingTime(lockedUntil - now) + "."
        );
    }

    public synchronized String enregistrerEchec(String login) {
        String key = normalize(login);
        long now = System.currentTimeMillis();

        Long lockedUntil = lockedUntilByLogin.get(key);
        if (lockedUntil != null && lockedUntil > now) {
            return "Trop de tentatives de connexion. Réessayez dans "
                + formatRemainingTime(lockedUntil - now) + ".";
        }

        int attempts = attemptsByLogin.getOrDefault(key, 0) + 1;
        attemptsByLogin.put(key, attempts);

        if (attempts >= MAX_ATTEMPTS) {
            long newLockedUntil = now + LOCK_DURATION_MS;
            lockedUntilByLogin.put(key, newLockedUntil);
            attemptsByLogin.remove(key);
            return "Compte temporairement bloqué après plusieurs échecs. Réessayez dans "
                + formatRemainingTime(LOCK_DURATION_MS) + ".";
        }

        int remaining = MAX_ATTEMPTS - attempts;
        return remaining == 1
            ? "Identifiant ou mot de passe incorrect. Dernière tentative avant blocage temporaire."
            : "Identifiant ou mot de passe incorrect. Il vous reste " + remaining + " tentatives.";
    }

    public synchronized void reinitialiser(String login) {
        String key = normalize(login);
        attemptsByLogin.remove(key);
        lockedUntilByLogin.remove(key);
    }

    private static String normalize(String login) {
        return login == null ? "" : login.trim().toLowerCase();
    }

    private static String formatRemainingTime(long durationMs) {
        long totalSeconds = Math.max(1, durationMs / 1000);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        if (minutes <= 0) {
            return seconds + " s";
        }
        if (seconds == 0) {
            return minutes + " min";
        }
        return minutes + " min " + seconds + " s";
    }
}
