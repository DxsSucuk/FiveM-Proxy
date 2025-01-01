package de.presti.fivemproxy.entity;

import de.presti.fivemproxy.main.Main;
import de.presti.fivemproxy.proxy.IProxy;
import de.presti.fivemproxy.states.ProxyTyp;
import de.presti.fivemproxy.utils.Logger;
import de.presti.fivemproxy.utils.ThreadUtil;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

public class TCPProxy implements IProxy<Socket> {

    private final int bindPort;

    ProxyTyp proxyTyp;

    private ServerSocket serverSocket;

    private final HashMap<Socket, TCPSocketContainer> playerConnections = new HashMap<>();

    public TCPProxy(int bindPort, ProxyTyp proxyTyp) {
        this.bindPort = bindPort;
        this.proxyTyp = proxyTyp;
    }

    @Override
    public void start() {
        try {
            if (proxyTyp == ProxyTyp.HTTPS) {
                serverSocket = Main.getSslContext().getServerSocketFactory().createServerSocket(bindPort);
            } else {
                serverSocket = new ServerSocket(bindPort);
            }
        } catch (Exception e) {
            Logger.logException(e, Logger.Service.TCP);
        }
    }

    @Override
    public int getPort() {
        return bindPort;
    }

    int i = 0;

    @Override
    public void run() {
        while (serverSocket.isBound() && !serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                ThreadUtil.createThread(x -> handleConnection(socket));
            } catch (Exception e) {
                Logger.logException(e, Logger.Service.TCP);
            }
        }

        stop();
    }

    @Override
    public void handleConnection(Socket socket) {
        Logger.logTCP(Logger.LogLevel.INFO, "Received TCP connection from " + socket.getRemoteSocketAddress() + "(" + i + ", SSL: " + (socket instanceof SSLSocket) + ")");

        FiveMEntry target = null;
        String hostname = null;

        if (socket instanceof SSLSocket sslSocket) {
            SSLSession session = sslSocket.getSession();
            hostname = session.getPeerHost();
        } else {
            hostname = socket.getInetAddress().getHostName();
        }

        target = Main.getFiveMEntryByProxy(hostname, Logger.Service.TCP);

        if (target == null) {
            Logger.logTCP(Logger.LogLevel.ERROR, "Failed to find target server for " + hostname + "(TCP," + i++ + ", SSL: " + (socket instanceof SSLSocket) + ")");
            try {
                socket.close();
            } catch (IOException e) {
                Logger.logException(e, Logger.Service.TCP);
            }
            return;
        }

        // Because Java keeps crying.
        FiveMEntry finalTarget = target;
        Socket targetSocket = null;
        try {
            targetSocket = new Socket(finalTarget.getAddress(), finalTarget.getPort(proxyTyp));
        } catch (IOException e) {
            Logger.logTCP(Logger.LogLevel.ERROR, "Failed to connect to target server (" + i++ + ", SSL: " + (socket instanceof SSLSocket) + ")");
            return;
        }

        TCPSocketContainer TCPSocketContainer = new TCPSocketContainer(socket, targetSocket, i++);

        playerConnections.put(socket, TCPSocketContainer);
        TCPSocketContainer.run();
    }

    @Override
    public void stop() {
        Logger.logTCP(Logger.LogLevel.INFO, "Stopping TCP Proxy");
        try {
            serverSocket.close();
        } catch (Exception e) {
            Logger.logException(e, Logger.Service.TCP);
        }
    }

}
