package de.presti.fivemproxy.entity;

import de.presti.fivemproxy.states.ProxyTyp;
import lombok.Getter;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;

@Getter
public class FiveMEntry {

    private final String ip;
    private final InetAddress address;

    private final int httpPort;
    private final int udpPort;

    private final String hostname;
    private final HashMap<ProxyTyp, Integer> proxyPorts = new HashMap<>();

    public FiveMEntry(String ip, String hostname, int httpPort, int udpPort) throws UnknownHostException {
        this.ip = ip;
        this.address = InetAddress.getByName(ip);
        this.hostname = hostname;
        this.httpPort = httpPort;
        this.udpPort = udpPort;
        proxyPorts.put(ProxyTyp.HTTP, httpPort);
        proxyPorts.put(ProxyTyp.HTTPS, httpPort);
        proxyPorts.put(ProxyTyp.GAME, udpPort);
    }

    public int getPort(ProxyTyp proxyTyp) {
        return proxyPorts.get(proxyTyp);
    }
}
