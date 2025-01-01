package de.presti.fivemproxy.utils;

import de.presti.fivemproxy.main.Main;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class UDPReachTester implements Runnable {

    DatagramSocket serverSocket;

    public void start(int port) throws SocketException {
        serverSocket = new DatagramSocket(port);
    }

    @Override
    public void run() {
        while (serverSocket.isBound() && !serverSocket.isClosed()) {
            try {
                byte[] buffer = new byte[Main.DEFAULT_BUFFER_SIZE];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                serverSocket.receive(packet);

                String hostname = packet.getAddress().getHostName();

                Logger.logUDP(Logger.LogLevel.INFO, "Hostname: " + hostname + "(UDP)");

                String identifier = packet.getAddress().getHostAddress() + ":" + packet.getPort();

                Logger.logUDP(Logger.LogLevel.INFO, "Received packet from " + identifier);

                serverSocket.send(new DatagramPacket(packet.getData(), packet.getLength(), packet.getAddress(), packet.getPort()));
                Logger.logUDP(Logger.LogLevel.INFO, "Sent packet back to " + identifier);
            } catch (SocketTimeoutException e) {
                // Ignore.
            } catch (Exception e) {
                Logger.logException(e, Logger.Service.UDP);
            }
        }
    }
}
