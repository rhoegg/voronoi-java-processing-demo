package com.ryanhoegg.voronoi.core;

public class CircleEventDebug {
    public static void main(String[] args) {
        // Replay one of the failing seeds from the stress test
        long seed = 4933566429088898398L; // First seed from the list
        System.out.println("Replaying seed: " + seed);
        CircleEventSelectorStressTest.replaySeed(seed);
    }
}
