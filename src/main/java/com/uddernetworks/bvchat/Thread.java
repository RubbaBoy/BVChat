package com.uddernetworks.bvchat;

public class Thread {
    public static void sleep(long milliseconds) {
        try {
            java.lang.Thread.sleep(milliseconds);
        } catch (InterruptedException ignored) {}
    }
}
