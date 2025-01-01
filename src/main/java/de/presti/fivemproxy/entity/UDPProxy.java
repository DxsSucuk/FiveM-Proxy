package de.presti.fivemproxy.entity;

import de.presti.fivemproxy.main.Main;
import de.presti.fivemproxy.proxy.IProxy;
import de.presti.fivemproxy.states.ProxyTyp;
import de.presti.fivemproxy.utils.Logger;
import de.presti.fivemproxy.utils.ThreadUtil;

import java.net.*;
import java.util.HashMap;

public class UDPProxy implements IProxy<DatagramPacket> {

    private final int bindPort;

    ProxyTyp proxyTyp;

    private DatagramSocket serverSocket;

    private final HashMap<String, UDPSocketContainer> playerConnections = new HashMap<>();

    public UDPProxy(int bindPort, ProxyTyp proxyTyp) {
        this.bindPort = bindPort;
        this.proxyTyp = proxyTyp;
    }

    @Override
    public void start() {
        try {
            serverSocket = new DatagramSocket(bindPort);
            serverSocket.setSoTimeout(Main.DEFAULT_TIMEOUT);
        } catch (Exception e) {
            Logger.logException(e, Logger.Service.UDP);
        }
    }

    @Override
    public int getPort() {
        return bindPort;
    }

    @Override
    public void run() {
        while (serverSocket.isBound() && !serverSocket.isClosed()) {
            try {
                byte[] buffer = new byte[Main.PAYLOAD_MAX_SIZE];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                serverSocket.receive(packet);

                ThreadUtil.createThread(x -> handleConnection(packet));
            } catch (SocketTimeoutException e) {
                // Ignore.
            } catch (Exception e) {
                Logger.logException(e, Logger.Service.UDP);
            }
        }

        stop();
    }


    int i = 0;

    @Override
    public void handleConnection(DatagramPacket packet) {

        String hostname = packet.getAddress().getHostName();

        FiveMEntry target = Main.getFiveMEntryByProxy(hostname, Logger.Service.UDP);

        if (target == null) {
            Logger.logUDP(Logger.LogLevel.ERROR, "Failed to find target server for " + hostname + "(UDP)");
            return;
        }

        String identifier = packet.getAddress().getHostAddress() + ":" + packet.getPort();

        Logger.logUDP(Logger.LogLevel.DEBUG, "Received UDP packet from " + identifier);

        if (playerConnections.containsKey(identifier)) {
            UDPSocketContainer udpSocketContainer = playerConnections.get(identifier);
            if (!udpSocketContainer.isClosed()) {
                udpSocketContainer.sendPacket(packet);
            } else {
                Logger.logUDP(Logger.LogLevel.INFO, "Removing connection for " + identifier);
                playerConnections.remove(identifier);
            }
        } else {
            Logger.logUDP(Logger.LogLevel.INFO, "Creating new connection for " + identifier);
            UDPSocketContainer udpSocketContainer = null;
            try {
                udpSocketContainer = new UDPSocketContainer(packet.getAddress(), packet.getPort(), target.getAddress(), target.getPort(proxyTyp), this, i++);
            } catch (SocketException e) {
                Logger.logUDP(Logger.LogLevel.ERROR, "Failed to create new connection for " + identifier);
                return;
            }

            playerConnections.put(identifier, udpSocketContainer);

            udpSocketContainer.run();
            udpSocketContainer.sendPacket(packet);
        }
    }

    @Override
    public void stop() {
        Logger.logUDP(Logger.LogLevel.INFO, "Stopping UDP Proxy");
        try {
            serverSocket.close();
        } catch (Exception e) {
            Logger.logException(e, Logger.Service.UDP);
        }
    }

    public void sendPacket(DatagramPacket packet, InetAddress address, int port) {
        Logger.logUDP(Logger.LogLevel.DEBUG, "Sending UDP packet to " + address.getHostAddress() + ":" + port);
        try {
            packet = new DatagramPacket(packet.getData(), packet.getData().length, address, port);
            serverSocket.send(packet);
        } catch (Exception e) {
            Logger.logException(e, Logger.Service.UDP);
        }
    }

}
