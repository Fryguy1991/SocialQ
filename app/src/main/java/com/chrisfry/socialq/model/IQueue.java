package com.chrisfry.socialq.model;

/**
 * Interface for general queue functionality
 */
public interface IQueue<T> {
    /**
     * Get item at the beginning of the queue
     *
     * @return - first item in the queue.
     */
    T get();

    /**
     * Add an item to the queue
     *
     * @param itemToAdd - item to add to the end of the queue
     */
    void add(T itemToAdd);

    /**
     * Get item at the beginning of the queue, but leave it in the queue
     *
     * @return - first item in the queue
     */
    T inspect();

    /**
     * Returns the number of items in the queue
     *
     * @return - number of items in queue
     */
    int getQueueLength();
}
