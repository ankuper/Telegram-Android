package org.telegram.messenger;

import android.text.TextUtils;

/**
 * Manages the lifecycle of the localhost SOCKS5 shim that tunnels
 * MTProto traffic through the Type3 (0xff-secret) proxy server.
 *
 * All methods are synchronized; call from any thread.
 * The native shim runs its own background thread internally.
 */
public class Type3ShimController {

    private static int    shimPort = 0;
    private static String shimUser = null;
    private static String shimPass = null;

    /**
     * Start (or restart) the shim for the given Type3 proxy.
     *
     * @param host      Type3 server host (TCP endpoint)
     * @param port      Type3 server port (typically 443)
     * @param wsPath    WebSocket/HTTP-stream path (e.g. "/"); use "/" if unknown
     * @param secretHex Full Type3 secret as hex string (starts with "ff")
     * @return true if the shim is now running
     */
    public static synchronized boolean start(String host, int port, String wsPath, String secretHex) {
        stop();
        if (TextUtils.isEmpty(host) || TextUtils.isEmpty(secretHex)) return false;
        if (TextUtils.isEmpty(wsPath)) wsPath = "/";

        int p = nativeStart(host, port, wsPath, secretHex);
        if (p == 0) return false;

        String[] creds = nativeCredentials();
        if (creds == null || creds.length < 2
                || TextUtils.isEmpty(creds[0]) || TextUtils.isEmpty(creds[1])) {
            nativeStop();
            return false;
        }
        shimPort = p;
        shimUser = creds[0];
        shimPass = creds[1];
        return true;
    }

    /** Stop the shim and clear credentials. No-op if not running. */
    public static synchronized void stop() {
        nativeStop();
        shimPort = 0;
        shimUser = null;
        shimPass = null;
    }

    public static synchronized boolean isRunning()  { return shimPort > 0; }
    public static synchronized int     getPort()    { return shimPort; }
    public static synchronized String  getUser()    { return shimUser != null ? shimUser : ""; }
    public static synchronized String  getPass()    { return shimPass != null ? shimPass : ""; }

    /**
     * Returns true if the secret hex string looks like a Type3 secret:
     * starts with "ff" (0xff marker) and is at least 36 hex chars
     * (1 marker byte + 16 key bytes = 17 bytes = 34 hex chars + ≥1 domain byte).
     */
    public static boolean isType3Secret(String secret) {
        return secret != null
                && secret.length() >= 36
                && (secret.startsWith("ff") || secret.startsWith("FF"));
    }

    // ---- JNI ----------------------------------------------------------------

    /** @return bound localhost port, or 0 on failure */
    private static native int nativeStart(String host, int port, String wsPath, String secretHex);
    private static native void nativeStop();
    /** @return {user, pass} or null */
    private static native String[] nativeCredentials();
}
