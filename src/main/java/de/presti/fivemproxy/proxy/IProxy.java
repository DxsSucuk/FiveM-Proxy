package de.presti.fivemproxy.proxy;

public interface IProxy<T> {

    public int getPort();

    public void start();
    public void stop();

    public void run();

    public void handleConnection(T connection);

}
