package de.presti.fivemproxy.main;

import de.presti.fivemproxy.entity.FiveMEntry;
import de.presti.fivemproxy.entity.TCPProxy;
import de.presti.fivemproxy.entity.UDPProxy;
import de.presti.fivemproxy.states.ProxyTyp;
import de.presti.fivemproxy.utils.Logger;
import de.presti.fivemproxy.utils.UDPReachTester;
import lombok.Getter;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class Main {

    private static final List<FiveMEntry> fiveMEntries = new ArrayList<>();

    static TCPProxy httpProxy, httpsProxy;
    static UDPProxy udpProxy;

    static Thread httpThread, httpsThread, udpThread;

    public static int PAYLOAD_MAX_SIZE = 1024 * 5, DEFAULT_TIMEOUT = 5000, DEFAULT_BUFFER_SIZE = 1024 * 2;

    @Getter
    static SSLContext sslContext;

    @Getter
    static boolean bypassCache = false, debug = false;

    public static void main(String[] args) throws IOException, KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyManagementException {

        if (args.length > 0 && args[0].equalsIgnoreCase("fuckCFX")) {
            Logger.log(Logger.LogLevel.INFO, "Starting UDP reach tester", Logger.Service.NONE);
            UDPReachTester udpReachTester = new UDPReachTester();
            udpReachTester.start(30120);
            new Thread(udpReachTester).start();
            return;
        }

        if (!Files.exists(Path.of("keys"))) {
            Logger.log(Logger.LogLevel.ERROR, "Please create a keys directory and put your certificates in there");
            Files.createDirectory(Path.of("keys"));
            System.exit(-1);
            return;
        }

        Path pathToCert = Path.of("keys/server.jks");
        if (!Files.exists(pathToCert)) {
            Logger.log(Logger.LogLevel.ERROR, "Please create a server.jks file in the keys directory");
            System.exit(-1);
            return;
        }

        Path pathToPassword = Path.of("keys/password.txt");
        if (!Files.exists(pathToPassword)) {
            Logger.log(Logger.LogLevel.ERROR, "Please create a password.txt file in the keys directory");
            System.exit(-1);
            return;
        }

        if (args.length < 6) {
            Logger.log(Logger.LogLevel.ERROR, "Usage: java -jar FiveMProxy.jar <bindAddress> <hostAddress> <target> <httpPort> <httpsPort> <udpPort>");
            System.exit(-1);
            return;
        }

        if (Arrays.stream(args).anyMatch(x -> x.equalsIgnoreCase("--no-log-udp"))) {
            Logger.log(Logger.LogLevel.INFO, "Not logging UDP packets");
            Logger.setAllowedServices(Arrays.stream(Logger.getAllowedServices()).filter(x -> x != Logger.Service.UDP).toArray(Logger.Service[]::new));
        }

        if (Arrays.stream(args).anyMatch(x -> x.equalsIgnoreCase("--no-log-tcp"))) {
            Logger.log(Logger.LogLevel.INFO, "Not logging TCP packets");
            Logger.setAllowedServices(Arrays.stream(Logger.getAllowedServices()).filter(x -> x != Logger.Service.TCP).toArray(Logger.Service[]::new));
        }

        if (Arrays.stream(args).anyMatch(x -> x.equalsIgnoreCase("--no-log-none"))) {
            Logger.log(Logger.LogLevel.INFO, "Not none packet stuff");
            Logger.setAllowedServices(Arrays.stream(Logger.getAllowedServices()).filter(x -> x != Logger.Service.NONE).toArray(Logger.Service[]::new));
        }

        if (Arrays.stream(args).anyMatch(x -> x.equalsIgnoreCase("--bypass-cache"))) {
            Logger.log(Logger.LogLevel.INFO, "Bypassing cache");
            bypassCache = true;
        }

        if (Arrays.stream(args).anyMatch(x -> x.equalsIgnoreCase("--debug"))) {
            Logger.log(Logger.LogLevel.INFO, "Debug mode enabled");
            debug = true;
        }

        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(pathToCert.toFile())) {
            keyStore.load(fis, Files.readString(pathToPassword).toCharArray());
        } catch (CertificateException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        // Initialize KeyManagerFactory
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, Files.readString(pathToPassword).toCharArray());

        // Initialize SSLContext
        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);

        String bindAddress = args[0];
        String hostAddress = args[1];
        String target = args[2];
        int httpPort = Integer.parseInt(args[3]);
        int httpsPort = Integer.parseInt(args[4]);
        int udpPort = Integer.parseInt(args[5]);

        Logger.log(Logger.LogLevel.INFO, "Starting proxies on " + bindAddress + " for " + target + " with ports " + httpPort + ", " + httpsPort + ", " + udpPort);

        if (Arrays.stream(args).noneMatch(x -> x.equalsIgnoreCase("--skip-http")))
            httpProxy = new TCPProxy(httpPort, ProxyTyp.HTTP);

        if (Arrays.stream(args).noneMatch(x -> x.equalsIgnoreCase("--skip-https")))
            httpsProxy = new TCPProxy(httpsPort, ProxyTyp.HTTPS);

        if (Arrays.stream(args).noneMatch(x -> x.equalsIgnoreCase("--skip-udp")))
            udpProxy = new UDPProxy(udpPort, ProxyTyp.GAME);

        Logger.log(Logger.LogLevel.INFO, "Given host address: " + hostAddress);
        fiveMEntries.add(new FiveMEntry(target, hostAddress, httpPort, udpPort));

        Logger.log(Logger.LogLevel.INFO, "Starting proxies...");

        runProxies();

        Logger.log(Logger.LogLevel.INFO, "Proxies started!");
        Logger.log(Logger.LogLevel.INFO, "Press CTRL + C to stop the proxies");
    }

    public static void runProxies() {
        if (httpProxy != null) httpProxy.start();
        if (httpsProxy != null) httpsProxy.start();
        if (udpProxy != null) udpProxy.start();

        if (httpProxy != null) httpThread = new Thread(() -> httpProxy.run());
        if (httpsProxy != null) httpsThread = new Thread(() -> httpsProxy.run());
        if (udpProxy != null) udpThread = new Thread(() -> udpProxy.run());

        if (httpProxy != null) {
            httpThread.start();
            Logger.log(Logger.LogLevel.INFO, "HTTP Proxy started on port " + httpProxy.getPort());
        }

        if (httpsProxy != null) {
            httpsThread.start();
            Logger.log(Logger.LogLevel.INFO, "HTTPS Proxy started on port " + httpsProxy.getPort());
        }

        if (udpProxy != null) {
            udpThread.start();
            Logger.log(Logger.LogLevel.INFO, "UDP Proxy started on port " + udpProxy.getPort());
        }
    }

    static HashMap<String, FiveMEntry> fiveMEntryCache = new HashMap<>();

    public static FiveMEntry getFiveMEntryByProxy(String proxyName, Logger.Service logService) {
        if (bypassCache) {
            return getFiveMEntryByProxyNoneCache(proxyName, logService);
        }

        if (fiveMEntryCache.containsKey(proxyName)) {
            return fiveMEntryCache.get(proxyName);
        }

        FiveMEntry entry = getFiveMEntryByProxyNoneCache(proxyName, logService);
        fiveMEntryCache.put(proxyName, entry);
        return entry;
    }

    public static FiveMEntry getFiveMEntryByProxyNoneCache(String proxyName, Logger.Service logService) {
        for (FiveMEntry entry : fiveMEntries) {
            if (entry.getHostname().equals(proxyName)) {
                Logger.log(Logger.LogLevel.SUCCESS, "Found target server for " + proxyName, logService);
                return entry;
            }
        }

        Logger.log(Logger.LogLevel.WARNING, "Failed to find target server for " + proxyName + " defaulting to first entry", logService);
        return fiveMEntries.getFirst();
    }

}
