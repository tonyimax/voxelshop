package com.vitco.util.action;

/**
 * So other objects can subscribe and get notified. Used by StateActionPrototype.
 */
public interface ChangeListener {
    void actionFired(boolean status);
}
