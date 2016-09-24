package model;

import android.util.Log;

import java.util.ArrayList;
import java.util.NoSuchElementException;


/**
 * Object for creating and accessing a queue of items
 */
//public class Queue<E> extends ArrayList implements java.util.Queue {
//
//    private int mMaxSize = -1;
//    public Queue() {
//
//    }
//
//    //Constructor for overriding maximum size of a queue
//    public Queue(int maxSize) {
//        mMaxSize = maxSize;
//    }
//
//    @Override
//    public boolean offer(E objectToAdd) {
//        if(mMaxSize < 0 || size() < mMaxSize) {
//            super.add(objectToAdd);
//            return true;
//        } else {
//            Log.d(this.getClass().toString(), "QUEUE HAS REACHED MAX SIZE");
//            return false;
//        }
//    }
//
//    @Override
//    public T remove() {
//
//    }
//
//    @Override
//    public T poll() {
//        if(isEmpty()) {
//            Log.d(this.getClass().toString(), "QUEUE IS EMPTY");
//        } else {
//            //Get item and remove from the queue
//            T itemToReturn = get(0);
//            remove(0);
//            return itemToReturn;
//        }
//        return null;
//    }
//
//    @Override
//    public T element() {
//        if(!isEmpty()) {
//            return get(0);
//        }
//        throw new NoSuchElementException("QUEUE IS EMPTY");
////    }
////
////    @Override
////    public T peek() {
////        return null;
////    }
//}
