package com.timgroup.statsd;

/**
 * A No-Op StatsDClient, which can be substituted in when metrics are not
 * required.
 * 
 * @author Tom Denley
 *
 */
public final class NoOpStatsDClient implements StatsDClient {
    public void stop() { }
    public void count(String aspect, int delta) { }
    public void incrementCounter(String aspect) { }
    public void increment(String aspect) { }
    public void decrementCounter(String aspect) { }
    public void decrement(String aspect) { }
    public void recordGaugeValue(String aspect, int value) { }
    public void gauge(String aspect, int value) { }
    public void recordExecutionTime(String aspect, int timeInMs) { }
    public void time(String aspect, int value) { }
}
