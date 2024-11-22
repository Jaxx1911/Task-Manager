package org.example;

import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

public class Test {

    public static void main(String[] args) throws InterruptedException {
        SystemInfo systemInfo = new SystemInfo();
        OperatingSystem os = systemInfo.getOperatingSystem();

        // Lấy một tiến trình mẫu
        OSProcess process = os.getProcess(os.getProcesses(o -> true, OperatingSystem.ProcessSorting.CPU_DESC, Integer.MAX_VALUE).get(0).getProcessID());

        // Lần đo thứ nhất
        long kernelTime1 = process.getKernelTime();
        long userTime1 = process.getUserTime();
        long time1 = System.currentTimeMillis();

        // Đợi một khoảng thời gian (ví dụ: 1 giây)
        Thread.sleep(1000);

        // Lần đo thứ hai
        process = os.getProcess(process.getProcessID()); // Cập nhật tiến trình
        long kernelTime2 = process.getKernelTime();
        long userTime2 = process.getUserTime();
        long time2 = System.currentTimeMillis();

        // Tính toán
        long deltaKernel = kernelTime2 - kernelTime1;
        long deltaUser = userTime2 - userTime1;
        long deltaTime = time2 - time1;

        double cpuUsage = (deltaKernel + deltaUser) * 100.0 / deltaTime;

        System.out.printf("CPU Usage: %.2f%%", cpuUsage);
    }
}



