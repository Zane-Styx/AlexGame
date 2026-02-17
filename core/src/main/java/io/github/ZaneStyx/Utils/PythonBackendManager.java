package io.github.ZaneStyx.Utils;

import com.badlogic.gdx.Gdx;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public final class PythonBackendManager {
    private static final Object LOCK = new Object();
    private static Process pythonServerProcess;
    private static volatile String lastStartError;
    private static volatile String lastLogLine;

    private PythonBackendManager() {
    }

    public static void startServer() {
        synchronized (LOCK) {
            if (pythonServerProcess != null && pythonServerProcess.isAlive()) {
                return;
            }
            try {
                File projectRoot = new File(System.getProperty("user.dir"));
                File venvPython = new File(projectRoot, ".venv/Scripts/python.exe");
                File serverScript = new File(projectRoot, "python/bridge_server.py");

                if (!serverScript.exists()) {
                    lastStartError = "bridge_server.py not found";
                    Gdx.app.error("PythonBackend", "bridge_server.py not found at " + serverScript.getAbsolutePath());
                    return;
                }

                String pythonExe = venvPython.exists() ? venvPython.getAbsolutePath() : "python";

                ProcessBuilder builder = new ProcessBuilder(
                    pythonExe,
                    serverScript.getAbsolutePath()
                );
                builder.directory(projectRoot);
                builder.environment().put("PYTHON_DEBUG_VIEW", "0");
                builder.redirectErrorStream(true);
                pythonServerProcess = builder.start();
                lastStartError = null;
                startLogPump();
            } catch (IOException e) {
                lastStartError = e.getMessage();
                Gdx.app.error("PythonBackend", "Failed to start Python backend", e);
            }
        }
    }

    public static boolean waitForReady(int timeoutMillis) {
        if (pythonServerProcess != null && !pythonServerProcess.isAlive()) {
            try {
                int exitCode = pythonServerProcess.exitValue();
                lastStartError = "Python backend exited (code " + exitCode + ")";
            } catch (IllegalThreadStateException ignored) {
                lastStartError = "Python backend exited";
            }
            pythonServerProcess = null;
        }
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            try (PythonBridgeClient client = new PythonBridgeClient("127.0.0.1", 9009)) {
                client.connect(1000);
                String response = client.sendRequest("ping", null);
                if (response != null && response.contains("\"ok\":true")) {
                    return true;
                }
            } catch (Exception ignored) {
            }

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return false;
    }

    public static void stopServer() {
        synchronized (LOCK) {
            if (pythonServerProcess == null) {
                return;
            }
            if (pythonServerProcess.isAlive()) {
                pythonServerProcess.destroy();
                try {
                    boolean exited = pythonServerProcess.waitFor(1500, TimeUnit.MILLISECONDS);
                    if (!exited && pythonServerProcess.isAlive()) {
                        pythonServerProcess.destroyForcibly();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    if (pythonServerProcess.isAlive()) {
                        pythonServerProcess.destroyForcibly();
                    }
                }
            }
            pythonServerProcess = null;
        }
    }

    public static String getLastStartError() {
        return lastStartError;
    }

    public static String getLastLogLine() {
        return lastLogLine;
    }

    private static void startLogPump() {
        if (pythonServerProcess == null) {
            return;
        }
        Thread pump = new Thread(() -> {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(pythonServerProcess.getInputStream())
            )) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lastLogLine = line;
                    Gdx.app.log("PythonBackend", line);
                }
            } catch (Exception ignored) {
            }
        }, "PythonBackend-LogPump");
        pump.setDaemon(true);
        pump.start();
    }
}
