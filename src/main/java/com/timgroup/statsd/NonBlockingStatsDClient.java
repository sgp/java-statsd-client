package com.timgroup.statsd;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

/**
 * A simple StatsD client implementation facilitating metrics recording.
 * 
 * <p>Upon instantiation, this client will establish a socket connection to a StatsD instance
 * running on the specified host and port. Metrics are then sent over this connection as they are
 * received by the client.
 * </p>
 * 
 * <p>Three key methods are provided for the submission of data-points for the application under
 * scrutiny:
 * <ul>
 *   <li>{@link #incrementCounter} - adds one to the value of the specified named counter</li>
 *   <li>{@link #recordGaugeValue} - records the latest fixed value for the specified named gauge</li>
 *   <li>{@link #recordExecutionTime} - records an execution time in milliseconds for the specified named operation</li>
 * </ul>
 * From the perspective of the application, these methods are non-blocking. Furthermore, these
 * methods are guaranteed not to throw an exception which may disrupt application execution.
 * </p>
 * 
 * <p>As part of a clean system shutdown, the {@link #stop()} method should be invoked
 * on any StatsD clients.</p>
 * 
 * @author Tom Denley
 *
 */
public final class NonBlockingStatsDClient implements StatsDClient {

    private static final StatsDClientErrorHandler NO_OP_HANDLER = new StatsDClientErrorHandler() {
        public void handle(Exception e) { /* No-op */ }
    };

    private final String prefix;
    private final DatagramChannel clientChannel;
    private final StatsDClientErrorHandler handler;
    private final InetSocketAddress targetAddress;

    /**
     * Create a new StatsD client communicating with a StatsD instance on the
     * specified host and port. All messages send via this client will have
     * their keys prefixed with the specified string. The new client will
     * attempt to open a connection to the StatsD server immediately upon
     * instantiation, and may throw an exception if that a connection cannot
     * be established. Once a client has been instantiated in this way, all
     * exceptions thrown during subsequent usage are consumed, guaranteeing
     * that failures in metrics will not affect normal code execution.
     * 
     * @param prefix
     *     the prefix to apply to keys sent via this client
     * @param hostname
     *     the host name of the targeted StatsD server
     * @param port
     *     the port of the targeted StatsD server
     * @throws StatsDClientException
     *     if the client could not be started
     */
    public NonBlockingStatsDClient(String prefix, String hostname, int port) throws StatsDClientException {
        this(prefix, hostname, port, NO_OP_HANDLER);
    }
    
    /**
     * Create a new StatsD client communicating with a StatsD instance on the
     * specified host and port. All messages send via this client will have
     * their keys prefixed with the specified string. The new client will
     * attempt to open a connection to the StatsD server immediately upon
     * instantiation, and may throw an exception if that a connection cannot
     * be established. Once a client has been instantiated in this way, all
     * exceptions thrown during subsequent usage are passed to the specified
     * handler and then consumed, guaranteeing that failures in metrics will
     * not affect normal code execution.
     * 
     * @param prefix
     *     the prefix to apply to keys sent via this client
     * @param hostname
     *     the host name of the targeted StatsD server
     * @param port
     *     the port of the targeted StatsD server
     * @param errorHandler
     *     handler to use when an exception occurs during usage
     * @throws StatsDClientException
     *     if the client could not be started
     */
    public NonBlockingStatsDClient(String prefix, String hostname, int port, StatsDClientErrorHandler errorHandler) throws StatsDClientException {
        this.prefix = prefix;
        this.handler = errorHandler;
        this.targetAddress = new InetSocketAddress(hostname, port);
        
        try {
            this.clientChannel = DatagramChannel.open();
        } catch (Exception e) {
            throw new StatsDClientException("Failed to start StatsD client", e);
        }
    }

    /**
     * Cleanly shut down this StatsD client. This method may throw an exception if
     * the socket cannot be closed.
     */
    public void stop() {
        if (clientChannel != null) {
            try {
                clientChannel.close();
            } catch (IOException ioe) {
                this.handler.handle(ioe);
            }
        }
    }

    /**
     * Adjusts the specified counter by a given delta.
     * 
     * <p>This method is non-blocking and is guaranteed not to throw an exception.</p>
     * 
     * @param aspect
     *     the name of the counter to adjust
     * @param delta
     *     the amount to adjust the counter by
     */
    public void count(String aspect, int delta) {
        send(String.format("%s.%s:%d|c", prefix, aspect, delta));
    }

    /**
     * Increments the specified counter by one.
     * 
     * <p>This method is non-blocking and is guaranteed not to throw an exception.</p>
     * 
     * @param aspect
     *     the name of the counter to increment
     */
    public void incrementCounter(String aspect) {
        count(aspect, 1);
    }

    /**
     * Convenience method equivalent to {@link #incrementCounter(String)}. 
     */
    public void increment(String aspect) {
        incrementCounter(aspect);
    }

    /**
     * Decrements the specified counter by one.
     * 
     * <p>This method is non-blocking and is guaranteed not to throw an exception.</p>
     * 
     * @param aspect
     *     the name of the counter to decrement
     */
    public void decrementCounter(String aspect) {
        count(aspect, -1);
    }

    /**
     * Convenience method equivalent to {@link #decrementCounter(String)}. 
     */
    public void decrement(String aspect) {
        decrementCounter(aspect);
    }

    /**
     * Records the latest fixed value for the specified named gauge (integers).
     * 
     * <p>This method is non-blocking and is guaranteed not to throw an exception.</p>
     * 
     * @param aspect
     *     the name of the gauge
     * @param value
     *     the new reading of the gauge as an int
     */
    public void recordGaugeValue(String aspect, int value) {
        send(String.format("%s.%s:%d|g", prefix, aspect, value));
    }

    /**
     * Records the latest fixed value for the specified named gauge (long).
     *
     * <p>This method is non-blocking and is guaranteed not to throw an exception.</p>
     *
     * @param aspect
     *     the name of the gauge
     * @param value
     *     the new reading of the gauge as a long
     */
    public void recordGaugeValue(String aspect, long value) {
        send(String.format("%s.%s:%d|g", prefix, aspect, value));
    }

    /**
     * Convenience method equivalent to {@link #recordGaugeValue(String, int)}. 
     */
    public void gauge(String aspect, int value) {
        recordGaugeValue(aspect, value);
    }

    /**
     * Convenience method equivalent to {@link #recordGaugeValue(String, long)}.
     */
    public void gauge(String aspect, long value) {
        recordGaugeValue(aspect, value);
    }

    /**
     * Records an execution time in milliseconds for the specified named operation.
     *
     * <p>This method is non-blocking and is guaranteed not to throw an exception.</p>
     *
     * @param aspect
     *     the name of the timed operation
     * @param timeInMs
     *     the time in milliseconds
     */
    public void recordExecutionTime(String aspect, int timeInMs) {
        send(String.format("%s.%s:%d|ms", prefix, aspect, timeInMs));
    }

    /**
     * Convenience method equivalent to {@link #recordExecutionTime(String, int)}. 
     */
    public void time(String aspect, int value) {
        recordExecutionTime(aspect, value);
    }

    private void send(final String message) {
        try {
            final byte[] sendData = message.getBytes();
            ByteBuffer buf = ByteBuffer.allocate(sendData.length);
            buf.put(sendData);
            buf.flip();
            clientChannel.send(buf, this.targetAddress);
        }
        catch (Exception e) {
            handler.handle(e);
        }
    }
}
