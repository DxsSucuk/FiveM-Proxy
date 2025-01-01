package de.presti.fivemproxy.entity;

import de.presti.fivemproxy.main.Main;
import de.presti.fivemproxy.utils.Logger;
import de.presti.fivemproxy.utils.ThreadUtil;

import java.io.IOException;
import java.net.*;

public class UDPSocketContainer {
    Thread receiveThread;

    UDPProxy proxy;

    InetAddress connectorAddress;
    int connectorPort;

    InetAddress targetAddress;
    int targetPort;

    DatagramSocket senderSocket;

    long containerId;

    public UDPSocketContainer(InetAddress connectorAddress, int connectorPort, InetAddress targetAddress, int targetPort, UDPProxy proxy, int containerId) throws SocketException {
        this.connectorAddress = connectorAddress;
        this.connectorPort = connectorPort;
        this.targetAddress = targetAddress;
        this.targetPort = targetPort;

        this.proxy = proxy;

        this.senderSocket = new DatagramSocket();
        this.senderSocket.setSoTimeout(Main.DEFAULT_TIMEOUT);
        this.senderSocket.connect(targetAddress, targetPort);
        this.senderSocket.setReceiveBufferSize(Main.PAYLOAD_MAX_SIZE);
        this.senderSocket.setReceiveBufferSize(Main.PAYLOAD_MAX_SIZE);
    }

    public void run() {
        Logger.logUDP(Logger.LogLevel.INFO, "Starting UDP Socket Container on port " + senderSocket.getLocalPort() + " ("+ containerId + ")");

        receiveThread = new Thread(() -> {
            boolean failure = false;
            while (senderSocket.isBound() && !senderSocket.isClosed() && !failure) {
                try {
                    byte[] buffer = new byte[Main.DEFAULT_BUFFER_SIZE];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    Logger.logUDP(Logger.LogLevel.DEBUG, "Waiting for UDP packet: " + containerId);
                    senderSocket.receive(packet);
                    ThreadUtil.createThread(x -> proxy.sendPacket(packet, connectorAddress, connectorPort));
                } catch (SocketTimeoutException e) {
                    Logger.logUDP(Logger.LogLevel.DEBUG, "Socket timeout, retrying (UDP, " + containerId + ")");
                } catch (IOException e) {
                    Logger.logUDP(Logger.LogLevel.ERROR, "Receiver failed, shutdown for safety (UDP, " + containerId + ")");
                    Logger.logException(e, Logger.Service.UDP);
                    failure = true;
                }
            }

            stop();
        });

        receiveThread.start();
    }

    public boolean isClosed() {
        return senderSocket.isClosed();
    }

    public void stop() {
        Logger.logUDP(Logger.LogLevel.INFO, "Stopping UDP Socket Container (" + containerId + ")");
        try {
            senderSocket.disconnect();
            senderSocket.close();
            receiveThread.interrupt();
        } catch (Exception e) {
            Logger.logException(e, Logger.Service.UDP);
        }
    }

    boolean sendFailed = false;

    public void sendPacket(DatagramPacket packet) {
        if (sendFailed) {
            return;
        }

        Logger.logUDP(Logger.LogLevel.DEBUG, "Sending UDP packet to " + targetAddress.getHostAddress() + ":" + targetPort + " (" + containerId + ")");
        try {
            packet = new DatagramPacket(packet.getData(), packet.getLength(), targetAddress, targetPort);
            senderSocket.send(packet);
        } catch (Exception e) {
            Logger.logUDP(Logger.LogLevel.ERROR, "Sender failed, shutdown for safety (UDP, " + containerId + ")");
            Logger.logException(e, Logger.Service.UDP);
            sendFailed = true;
            stop();
        }
    }
}
