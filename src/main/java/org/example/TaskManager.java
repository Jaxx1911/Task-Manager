package org.example;

import oshi.SystemInfo;
import oshi.software.os.OperatingSystem;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.HashMap;
import java.util.Map;

public class TaskManager extends JFrame {
    private JTable table;
    private DefaultTableModel tableModel;
    private SystemInfo systemInfo;
    private OperatingSystem os;
    private TableRowSorter<DefaultTableModel> sorter;
    private JLabel processInfoLabel;
    int logicalProcessorCount;

    private Map<Integer, ProcessStats> processStatsMap = new HashMap<>();


    public TaskManager() {

        setTitle("Task Manager");
        setSize(1000, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Khởi tạo OSHI
        systemInfo = new SystemInfo();
        os = systemInfo.getOperatingSystem();
        logicalProcessorCount =  systemInfo.getHardware().getProcessor().getLogicalProcessorCount();
        System.out.println(logicalProcessorCount);

        // Tạo bảng hiển thị tiến trình
        String[] columnNames = {
                "Process Name", "PID", "CPU Usage (%)", "CPU Time", "Memory Usage", "Threads", "User"
        };

        tableModel = new DefaultTableModel(columnNames, 0);
        table = new JTable(tableModel);
        sorter = new TableRowSorter<>(tableModel);
        sorter.setComparator(4, (o1,o2) -> Double.compare(parseMemoryToMB(o1.toString()) , parseMemoryToMB(o2.toString())));

        table.setRowSorter(sorter);
        // Thêm bảng vào ScrollPane
        JScrollPane scrollPane = new JScrollPane(table);

        // Thêm thanh tìm kiếm
        JTextField searchField = new JTextField(20);
        searchField.addActionListener(e -> filterTable(searchField.getText()));

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(new JLabel("Search: "), BorderLayout.WEST);
        topPanel.add(searchField, BorderLayout.CENTER);
        //Thêm nút kill
        JButton killButton = new JButton("Kill Process");
        killButton.addActionListener(e -> killSelectedProcess());
        //Thông tin process đc chọn
        processInfoLabel = new JLabel("No process selected");
        processInfoLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(killButton, BorderLayout.NORTH);
        bottomPanel.add(processInfoLabel, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        //Chọn process
        table.getSelectionModel().addListSelectionListener(e -> updateProcessInfo());

        // Update real-time
        Timer timer = new Timer(1000, e -> updateProcessTable());
        timer.start();
    }

    //Update
    private void updateProcessTable() {
        // Lưu lại PID của hàng đang được chọn
        int selectedRow = table.getSelectedRow();
        String selectedPid = null;
        if (selectedRow != -1) {
            selectedPid = tableModel.getValueAt(selectedRow, 1).toString();
        }

        tableModel.setRowCount(0);

        //GetProcess
        os.getProcesses(
                null,
                OperatingSystem.ProcessSorting.CPU_DESC,
                Integer.MAX_VALUE
        ).forEach(process -> {
            String processName = process.getName();
            int pid = process.getProcessID();
            double cpuUsage = (100d * (process.getKernelTime() + process.getUserTime())/ process.getUpTime() )/ logicalProcessorCount;
            long cpuTime = process.getKernelTime() + process.getUserTime();
            String formattedCpuTime = formatCpuTime(cpuTime);
            String memoryUsage = humanReadableByteCountBin(process.getResidentSetSize());
            int threads = process.getThreadCount();
            String user = process.getUser(); // Lấy user của tiến trình

            tableModel.addRow(new Object[]{
                    processName, pid, String.format("%.2f", cpuUsage), formattedCpuTime, memoryUsage, threads, user
            });
        });

        // Khôi phục trạng thái chọn
        if (selectedPid != null) {
            for (int row = 0; row < tableModel.getRowCount(); row++) {
                if (tableModel.getValueAt(row, 1).toString().equals(selectedPid)) {
                    table.setRowSelectionInterval(row, row);
                    break;
                }
            }
        }
    }

    private String formatCpuTime(long ticks) {
        // Tính số giây từ ticks (1 tick = 1/100 giây)
        long totalSeconds = ticks / 100;

        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    //Tham khảo https://stackoverflow.com/questions/55908151/calculate-memory-taken-by-a-processjob-using-oshi-lib
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

    private void filterTable(String query) {
        if (query.trim().isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + query));
        }
    }

    private void updateProcessInfo() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow != -1) {
            String processName = tableModel.getValueAt(selectedRow, 0).toString();
            String pid = tableModel.getValueAt(selectedRow, 1).toString();
            processInfoLabel.setText("Selected Process: " + processName + " (PID: " + pid + ")");
        } else {
            processInfoLabel.setText("No process selected");
        }
    }

    private void killSelectedProcess() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a process to kill!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // Lấy PID
        int pid = Integer.parseInt(tableModel.getValueAt(selectedRow, 1).toString());

        // Kill tiến trình
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
    private double parseMemoryToMB(String memoryUsage) {
        try {
            // Loại bỏ đơn vị và chuyển đổi sang số
            if (memoryUsage.endsWith(" GiB")) {
                return Double.parseDouble(memoryUsage.replace(" GiB", "")) * 1024;
            } else if (memoryUsage.endsWith(" MiB")) {
                return Double.parseDouble(memoryUsage.replace(" MiB", ""));
            } else if (memoryUsage.endsWith(" KiB")) {
                return Double.parseDouble(memoryUsage.replace(" KiB", "")) / 1024;
            } else if (memoryUsage.endsWith(" B")) {
                return Double.parseDouble(memoryUsage.replace(" B", "")) / (1024 * 1024);
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return 0;
    }
}