package org.example;

import java.util.HashMap;
import java.util.Map;

class ProcessStats {
    long previousKernelTime;
    long previousUserTime;
    long previousUpdateTime;

    public ProcessStats(long kernelTime, long userTime, long updateTime) {
        this.previousKernelTime = kernelTime;
        this.previousUserTime = userTime;
        this.previousUpdateTime = updateTime;
    }
}