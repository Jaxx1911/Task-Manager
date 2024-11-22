# Task Manager

## Công thức tính CPU Usage (%)

Công thức để tính mức sử dụng CPU của một tiến trình:

`CPU Usage (%) = (Δ (Kernel Time + User Time) / Δ Total Time) * 100`

- **Code Java**

```java
double cpuUsage = 0.0;

        // Tính toán CPU Usage dựa trên trạng thái trước đó
        if (processStatsMap.containsKey(pid)) {
            ProcessStats stats = processStatsMap.get(pid);
            long deltaKernelTime = kernelTime - stats.previousKernelTime;
            long deltaUserTime = userTime - stats.previousUserTime;
            long deltaTime = currentTime - stats.previousUpdateTime;

            if (deltaTime > 0) {
                cpuUsage = (100.0 * (deltaKernelTime + deltaUserTime)) / (deltaTime * logicalProcessorCount);
            }
        }

        // Cập nhật trạng thái mới
        processStatsMap.put(pid, new ProcessStats(kernelTime, userTime, currentTime));
```

Trong đó:
- **Kernel Time**: Thời gian tiến trình dành cho các tác vụ trong kernel space.
- **User Time**: Thời gian tiến trình dành cho các tác vụ trong user space.
- **Total Time**: Khoảng thời gian thực tế giữa hai lần đo.

## Công thức tính RAM Usage (%)

Công thức để tính mức sử dụng RAM của một tiến trình:

`RAM Usage (%) = (Resident Set Size (RSS) / Total Physical Memory) * 100`

- **Code Java (chỉ tính bộ nhớ vật lý process đang sử dụng - RRS)**

```java
process.getResidentSetSize()
```
- **Hàm chuyển về dạng readable MiB, KiB, GiB tham khảo**

https://stackoverflow.com/questions/55908151/calculate-memory-taken-by-a-processjob-using-oshi-lib

```java
public String humanReadableByteCountBin(long bytes) {
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024) {
            return bytes + " B";
        }
        long value = absB;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        return String.format("%.1f %ciB", value / 1024.0, ci.current());
    }
```

Trong đó:
- **Resident Set Size (RSS)**: Bộ nhớ vật lý tiến trình đang sử dụng.
- **Total Physical Memory**: Tổng dung lượng RAM vật lý trên hệ thống.

# Chi tiết code

## Phần Khởi Tạo Ứng Dụng
```java
public TaskManager() {
    setTitle("Task Manager");
    setSize(1000, 500);
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    setLocationRelativeTo(null);
}
```

- **Khởi Tạo OSHI**
```java
systemInfo = new SystemInfo();
os = systemInfo.getOperatingSystem();
logicalProcessorCount = systemInfo.getHardware().getProcessor().getLogicalProcessorCount();
```

- **Khởi Tạo Bảng Hiển Thị Tiến Trình**
```java
String[] columnNames = {
    "Process Name", "PID", "CPU Usage (%)", "CPU Time", "Memory Usage", "Threads", "User"
};
tableModel = new DefaultTableModel(columnNames, 0);
table = new JTable(tableModel);
```

- **Sắp Xếp Bảng**
```java
sorter = new TableRowSorter<>(tableModel);
sorter.setComparator(4, (o1, o2) -> Double.compare(parseMemoryToMB(o1.toString()), parseMemoryToMB(o2.toString())));
table.setRowSorter(sorter);
```

- **Thanh Tìm Kiếm**
```java
JTextField searchField = new JTextField(20);
searchField.addActionListener(e -> filterTable(searchField.getText()));
```

- **Hiển Thị Chi Tiết Tiến Trình Được Chọn**
```java
processInfoLabel = new JLabel("No process selected");
processInfoLabel.setHorizontalAlignment(SwingConstants.CENTER);
```

- **Nút dừng tiến trình**
```java
JButton killButton = new JButton("Kill Process");
killButton.addActionListener(e -> killSelectedProcess());
```

- **Cập nhật bảng thời gian thực (1s)**
```java
Timer timer = new Timer(1000, e -> updateProcessTable());
timer.start();
```

## Các hàm xử lý tiến trình

- **Hàm update**
```java
os.getProcesses(
                null,
                OperatingSystem.ProcessSorting.CPU_DESC,
                Integer.MAX_VALUE
        ).forEach(process -> {
            String processName = process.getName();
            int pid = process.getProcessID();

            long currentKernel = process.getKernelTime(), currentUserTime = process.getUserTime(), currentTime = System.currentTimeMillis();
            double cpuUsage = 0.0;

            // Tính toán CPU Usage dựa trên trạng thái trước đó
            if (processStatsMap.containsKey(pid)) {
                ProcessStats stats = processStatsMap.get(pid);
                long deltaKernelTime = currentKernel - stats.previousKernelTime;
                long deltaUserTime = currentUserTime - stats.previousUserTime;
                long deltaTime = currentTime - stats.previousUpdateTime;

                if (deltaTime > 0) {
                    cpuUsage = (100.0 * (deltaKernelTime + deltaUserTime)) / (deltaTime);
                }
            }
            processStatsMap.put(pid, new ProcessStats(currentKernel, currentUserTime, currentTime));

            long cpuTime = process.getKernelTime() + process.getUserTime();
            String formattedCpuTime = formatCpuTime(cpuTime);
            String memoryUsage = humanReadableByteCountBin(process.getResidentSetSize());
            int threads = process.getThreadCount();
            String user = process.getUser(); // Lấy user của tiến trình

            tableModel.addRow(new Object[]{
                    processName, pid, String.format("%.2f", cpuUsage), formattedCpuTime, memoryUsage, threads, user
            });
        });
```
  -	Mục đích:
  	-	Lấy danh sách các tiến trình từ OSHI.
  	-	Tính toán thông tin tiến trình như:
  	-	CPU Usage: Mức sử dụng CPU theo phần trăm.
  	-	CPU Time: Thời gian CPU đã sử dụng, định dạng hh:mm:ss.
  	-	Memory Usage: Bộ nhớ tiến trình sử dụng, hiển thị dưới dạng KiB, MiB, hoặc GiB.

- **Hàm dừng tiến trình**
```java
private void killSelectedProcess() {
    int selectedRow = table.getSelectedRow();
    if (selectedRow == -1) {
        JOptionPane.showMessageDialog(this, "Please select a process to kill!", "Error", JOptionPane.ERROR_MESSAGE);
        return;
    }
    int pid = Integer.parseInt(tableModel.getValueAt(selectedRow, 1).toString());

    try {
        boolean isKilled = ProcessHandle.of(pid).map(ProcessHandle::destroy).orElse(false);
        if (isKilled) {
            JOptionPane.showMessageDialog(this, "Process killed successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, "Failed to kill the process!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    } catch (Exception ex) {
        JOptionPane.showMessageDialog(this, "Error killing process: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }

    updateProcessTable();
}
```

## Hàm format dữ liệu

- **Hàm định dạng thời gian**
```java
private String formatCpuTime(long ticks) {
    long totalSeconds = ticks / 100;
    long hours = totalSeconds / 3600;
    long minutes = (totalSeconds % 3600) / 60;
    long seconds = totalSeconds % 60;

    return String.format("%02d:%02d:%02d", hours, minutes, seconds);
}

```
- **Formate bộ nhớ**
```java
public String humanReadableByteCountBin(long bytes) {
    long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
    if (absB < 1024) {
        return bytes + " B";
    }
    long value = absB;
    CharacterIterator ci = new StringCharacterIterator("KMGTPE");
    for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
        value >>= 10;
        ci.next();
    }
    value *= Long.signum(bytes);
    return String.format("%.1f %ciB", value / 1024.0, ci.current());
}
```




  
