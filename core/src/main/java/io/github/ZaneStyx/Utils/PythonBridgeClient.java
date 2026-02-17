package io.github.ZaneStyx.Utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Minimal JSON-over-TCP client for python/bridge_server.py.
 *
 * Usage:
 * PythonBridgeClient client = new PythonBridgeClient("127.0.0.1", 9009);
 * String response = client.sendRequest("ping", null);
 * client.close();
 */
public class PythonBridgeClient implements AutoCloseable {
    private final String host;
    private final int port;

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;

    public PythonBridgeClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connect(int timeoutMillis) throws IOException {
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            return;
        }
        closeInternal();
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMillis);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    /**
     * Send a request with a method and JSON payload string (object or null).
     *
     * @param method method name, e.g., "ping"
     * @param payloadJson JSON object string (without trailing newline), or null for {}
     * @return raw response JSON string
     */
    public String sendRequest(String method, String payloadJson) throws IOException {
        ensureConnected();
        String payload = (payloadJson == null || payloadJson.isBlank()) ? "{}" : payloadJson;
        String request = "{\"method\":\"" + escape(method) + "\",\"payload\":" + payload + "}\n";
        writer.write(request);
        writer.flush();
        String response = reader.readLine();
        if (response == null) {
            closeInternal();
            throw new IOException("connection_closed");
        }
        return response;
    }

    private void ensureConnected() throws IOException {
        if (socket == null || !socket.isConnected()) {
            connect(2000);
        }
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public void close() throws IOException {
        closeInternal();
    }

    private void closeInternal() throws IOException {
        if (writer != null) {
            writer.close();
            writer = null;
        }
        if (reader != null) {
            reader.close();
            reader = null;
        }
        if (socket != null) {
            socket.close();
            socket = null;
        }
    }
}
