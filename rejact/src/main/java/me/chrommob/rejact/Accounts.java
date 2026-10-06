package me.chrommob.rejact;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Password accounts, backed by the durable {@link Store}. Passwords are never stored: each account
 * keeps an iteration count, a random salt and a PBKDF2-HMAC-SHA256 hash, and verification is a
 * constant-time comparison. The record format is {@code iterations.b64(salt).b64(hash)} under the
 * {@code user:<name>} key of the shared {@code accounts} app store.
 */
public final class Accounts {
    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;

    private final Store.Kv store;
    private final SecureRandom random = new SecureRandom();

    public Accounts(Store store) {
        this.store = store.app("accounts");
    }

    /** Registers a new account. Returns false when the name is taken or the input is unusable. */
    public synchronized boolean register(String name, String password) {
        String user = clean(name);
        if (user.isEmpty() || password.length() < 4 || has(user)) {
            return false;
        }
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS);
        String record = ITERATIONS + "."
                + Base64.getEncoder().encodeToString(salt) + "."
                + Base64.getEncoder().encodeToString(hash);
        store.set("user:" + user, record);
        return true;
    }

    /** Constant-time credential check. No error detail on purpose: it must not leak which exists. */
    public boolean verify(String name, String password) {
        String user = clean(name);
        if (user.isEmpty()) {
            return false;
        }
        String record = store.get("user:" + user, "");
        if (record.isEmpty()) {
            // Spend comparable time so a missing user is indistinguishable from a wrong password.
            pbkdf2(password, new byte[SALT_BYTES], ITERATIONS);
            return false;
        }
        try {
            String[] parts = record.split("\\.");
            int iterations = Integer.parseInt(parts[0]);
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);
            byte[] actual = pbkdf2(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public boolean has(String name) {
        String user = clean(name);
        return !user.isEmpty() && !store.get("user:" + user, "").isEmpty();
    }

    public Set<String> names() {
        Set<String> out = new java.util.LinkedHashSet<>();
        for (String key : store.keys()) {
            if (key.startsWith("user:")) {
                out.add(key.substring("user:".length()));
            }
        }
        return out;
    }

    private static String clean(String name) {
        String user = name == null ? "" : name.trim();
        return user.matches("[a-zA-Z0-9_.-]{2,32}") ? user : "";
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BYTES * 8);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 unavailable", e);
        }
    }
}
