package de.presti.fivemproxy.entity;

import de.presti.fivemproxy.main.Main;
import de.presti.fivemproxy.utils.Logger;

import java.io.IOException;
import java.net.Socket;

public class TCPSocketContainer {
    Thread sendThread, receiveThread;
    Socket connectorSocket, targetSocket;
    long containerId;

    boolean isSSL;

    public TCPSocketContainer(Socket connectorSocket, Socket targetSocket, long containerId) {
        this.connectorSocket = connectorSocket;
        this.targetSocket = targetSocket;
        this.containerId = containerId;
        isSSL = connectorSocket instanceof javax.net.ssl.SSLSocket;

    }

    public void run() {
        sendThread = new Thread(() -> {
            boolean failure = false;
            while(connectorSocket.isConnected() && targetSocket.isConnected() && !failure) {
                try {
                    byte[] buffer = new byte[Main.DEFAULT_BUFFER_SIZE];
                    int read;
                    while ((read = connectorSocket.getInputStream().read(buffer)) > 0) {
                        targetSocket.getOutputStream().write(buffer, 0, read);
                    }
                } catch (IOException e) {
                    Logger.logTCP(Logger.LogLevel.ERROR, "Sender failed, shutdown for safety (TCP, " + containerId + ", SSL: " + isSSL + ")");
                    Logger.logException(e, Logger.Service.TCP);
                    failure = true;
                }
            }

            stop();
        });

        sendThread.start();

        receiveThread = new Thread(() -> {
            boolean failure = false;
            while(targetSocket.isConnected() && connectorSocket.isConnected() && !failure) {
                try {
                    byte[] buffer = new byte[Main.DEFAULT_BUFFER_SIZE];
                    int read;
                    while ((read = targetSocket.getInputStream().read(buffer)) > 0) {
                        connectorSocket.getOutputStream().write(buffer, 0, read);
                    }
                } catch (IOException e) {
                    Logger.logTCP(Logger.LogLevel.ERROR, "Receiver failed, shutdown for safety (TCP " + containerId +", SSL: " + isSSL + ")");
                    Logger.logException(e, Logger.Service.TCP);
                    failure = true;
                }
            }

            stop();
        });

        receiveThread.start();
    }

    public void stop() {
        Logger.logTCP(Logger.LogLevel.INFO, "Stopping TCP Socket Container (" + containerId + ", SSL: " + isSSL + ")");
        try {
            connectorSocket.close();
            targetSocket.close();
            receiveThread.interrupt();
            sendThread.interrupt();
        } catch (Exception e) {
            Logger.logException(e, Logger.Service.TCP);
        }
    }
}
