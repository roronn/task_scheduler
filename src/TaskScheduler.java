import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.toedter.calendar.JDateChooser;

import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Calendar;

public class TaskScheduler extends JFrame {

    // --- Configuration Constants ---
    private static final String CSV_FILE_PATH = "data/testdata.csv";
    private static final String EXPORT_DIRECTORY = "ExportData";
    private static final String[] CSV_HEADERS = {"ID", "Title", "Due Date (dd/MM/yy HH:MM)", "Priority", "Status"};
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yy");
    private static final SimpleDateFormat DATETIME_FORMAT = new SimpleDateFormat("dd/MM/yy HH:mm");

    private final DefaultTableModel tableModel;
    private final JTable priorityTable;
    private final JDateChooser dueDateChooser;
    private final JTextField titleField;
    private final JTextField timeInputField;
    private final JButton addTaskButton;
    private final JButton editTaskButton;
    private final JButton exportButton;

    private int editingTaskId = -1; // -1 means no task is currently being edited

    public TaskScheduler() {
        setTitle("Task Scheduler");
        setSize(700, 500);
        setResizable(false);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // --- Top Panel Components ---
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 15, 10));
        topPanel.add(new JLabel("Title:"));
        Font titleFont = new Font("SansSerif", Font.PLAIN, 14);
        titleField = new JTextField(20);
        titleField.setFont(titleFont);
        topPanel.add(titleField);

        topPanel.add(new JLabel("Due Date:"));
        dueDateChooser = new JDateChooser();
        dueDateChooser.setDateFormatString("dd/MM/yy");
        dueDateChooser.setDate(new Date());
        dueDateChooser.setPreferredSize(new Dimension(100, 25));
        topPanel.add(dueDateChooser);

        timeInputField = new JTextField(5);
        timeInputField.setText("13:00");
        topPanel.add(timeInputField);

        addTaskButton = new JButton("Add Task");
        topPanel.add(addTaskButton);
        add(topPanel, BorderLayout.NORTH);

        // --- Table Setup ---
        String[] columns = {"ID", "Title", "Due Date (dd/mm/yy)", "Priority", "Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 3 || column == 4;
            }
        };

        priorityTable = new JTable(tableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);

                String dueDateString = (String) getModel().getValueAt(row, 2);
                String status = (String) getModel().getValueAt(row, 4);

                if (this.isRowSelected(row)) {
                    c.setForeground(getSelectionForeground());
                } else {
                    c.setForeground(getForeground());
                }

                if (!this.isRowSelected(row)) {
                    if ("Finish".equalsIgnoreCase(status)) {
                        c.setForeground(new Color(0, 150, 0));
                    } else {
                        Date dueDate;
                        try {
                            dueDate = DATETIME_FORMAT.parse(dueDateString);
                        } catch (ParseException e) {
                            System.err.println("Error parsing due date for rendering: " + dueDateString + " - " + e.getMessage());
                            c.setForeground(getForeground());
                            return c;
                        }

                        Date now = new Date();

                        Calendar calDueDate = Calendar.getInstance();
                        calDueDate.setTime(dueDate);
                        calDueDate.set(Calendar.HOUR_OF_DAY, 0);
                        calDueDate.set(Calendar.MINUTE, 0);
                        calDueDate.set(Calendar.SECOND, 0);
                        calDueDate.set(Calendar.MILLISECOND, 0);

                        Calendar calNow = Calendar.getInstance();
                        calNow.setTime(now);
                        calNow.set(Calendar.HOUR_OF_DAY, 0);
                        calNow.set(Calendar.MINUTE, 0);
                        calNow.set(Calendar.SECOND, 0);
                        calNow.set(Calendar.MILLISECOND, 0);

                        if (calDueDate.before(calNow)) {
                            c.setForeground(Color.RED);
                        } else if (calDueDate.equals(calNow) || calDueDate.before(addDays(calNow.getTime(), 3))) {
                            c.setForeground(Color.BLACK);
                        } else {
                            c.setForeground(getForeground());
                        }
                    }
                }
                return c;
            }
        };

        priorityTable.setFillsViewportHeight(true);
        JScrollPane priorityScrollPane = new JScrollPane(priorityTable);
        add(priorityScrollPane, BorderLayout.CENTER);

        TableColumn priorityColumn = priorityTable.getColumnModel().getColumn(3);
        priorityColumn.setCellEditor(new DefaultCellEditor(new JComboBox<>(new String[]{"High", "Medium", "Low"})));

        TableColumn statusColumn = priorityTable.getColumnModel().getColumn(4);
        statusColumn.setCellEditor(new DefaultCellEditor(new JComboBox<>(new String[]{"In process", "Finish"})));

        tableModel.addTableModelListener(e -> {
            if (e.getType() == TableModelEvent.UPDATE) {
                int row = e.getFirstRow();
                int column = e.getColumn();
                if (column == 3 || column == 4) {
                    int taskId = Integer.parseInt((String) tableModel.getValueAt(row, 0));
                    String newPriority = (String) tableModel.getValueAt(row, 3);
                    String newStatus = (String) tableModel.getValueAt(row, 4);
                    updateTaskInCsv(taskId, newPriority, newStatus);
                    priorityTable.repaint();
                }
            }
        });

     // --- Bottom Panel Components ---
        JPanel mainBottomPanel = new JPanel(new BorderLayout());

        // Panel for Edit and Delete buttons (bottom-center)
        JPanel bottomCenterPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10)); // FlowLayout.CENTER for center alignment
        editTaskButton = new JButton("Edit Task");
        bottomCenterPanel.add(editTaskButton);
        JButton deleteButton = new JButton("Delete Task");
        bottomCenterPanel.add(deleteButton);
        mainBottomPanel.add(bottomCenterPanel, BorderLayout.CENTER); // Place in the CENTER of mainBottomPanel

        // Panel for Export button (bottom-right)
        JPanel bottomRightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10)); // FlowLayout.RIGHT for right alignment
        exportButton = new JButton("Export Tasks");
        bottomRightPanel.add(exportButton);
        mainBottomPanel.add(bottomRightPanel, BorderLayout.EAST); 

        // Add the main bottom panel to the frame's SOUTH region
        add(mainBottomPanel, BorderLayout.SOUTH);

        // --- Action Listeners ---
        addTaskButton.addActionListener(e -> addTask());
        deleteButton.addActionListener(e -> deleteSelectedTask());
        exportButton.addActionListener(e -> exportTasksToCsv());

        editTaskButton.addActionListener(e -> {
            if (editingTaskId != -1) { // If currently editing, save changes
                saveEditedTask();
            } else { // Otherwise, load selected task for editing
                loadSelectedTaskForEdit();
            }
        });


        loadTasks();

        setLocationRelativeTo(null);
        setVisible(true);
    }

    // --- CSV File Management Methods  ---

    private List<String[]> readAllCsvRows() {
        List<String[]> rows = new ArrayList<>();
        File csvFile = new File(CSV_FILE_PATH);

     // CSV file always exists and has valid headers 
        try (CSVReader reader = new CSVReader(new FileReader(csvFile))) {
            reader.readNext();
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) rows.add(nextLine);
        } catch (IOException | com.opencsv.exceptions.CsvValidationException e) {
             
        	// If the file doesn't exist, or is corrupted, this will throw RuntimeException.
        	throw new RuntimeException("Error reading CSV: " + e.getMessage(), e);
        }
        return rows;
    }

    private boolean writeAllCsvRows(List<String[]> dataRows) {
        try (CSVWriter writer = new CSVWriter(new FileWriter(CSV_FILE_PATH, false))) {
            writer.writeNext(CSV_HEADERS);
            writer.writeAll(dataRows);
            return true;
        } catch (IOException e) {
        	throw new RuntimeException("Error writing CSV: " + e.getMessage(), e);
        }
    }

    // --- Core Application Logic Methods ---

    private void loadTasks() {
        tableModel.setRowCount(0);
        readAllCsvRows().forEach(tableModel::addRow);
        sortTasksInTable();
    }

    private int getNextId() {
        int maxId = 0;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            maxId = Math.max(maxId, Integer.parseInt(String.valueOf(tableModel.getValueAt(i, 0))));
        }
        return maxId + 1;
    }

    private void addTask() {
        String title = titleField.getText().trim();
        Date selectedDate = dueDateChooser.getDate();
        String timeText = timeInputField.getText().trim();
        String combinedDateTime = DATE_FORMAT.format(selectedDate) + " " + timeText;

        if (title.isEmpty() || selectedDate == null || !timeText.matches("^(0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]$")) {
            JOptionPane.showMessageDialog(this, "All fields (Title, Due Date, Time) are required and valid.", "Input Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        
        String[] newTaskData = {String.valueOf(getNextId()), title, combinedDateTime, "Medium", "In process"};

        List<String[]> currentTasks = readAllCsvRows();
        currentTasks.add(newTaskData);

        if (writeAllCsvRows(currentTasks)) {
            clearInputFields();
            loadTasks();
        }
    }

    private void deleteSelectedTask() {
        int selectedRow = priorityTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a task to delete.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (editingTaskId != -1 && Integer.parseInt((String) tableModel.getValueAt(selectedRow, 0)) == editingTaskId) {
            resetEditModeUI();
        }
        if (priorityTable.isEditing()) priorityTable.getCellEditor().stopCellEditing();

        int taskIdToDelete = Integer.parseInt((String) tableModel.getValueAt(selectedRow, 0));
        List<String[]> updatedTasks = new ArrayList<>();
        readAllCsvRows().stream()
                .filter(task -> !task[0].equals(String.valueOf(taskIdToDelete)))
                .forEach(updatedTasks::add);

        if (writeAllCsvRows(updatedTasks)) loadTasks();
    }

    private void updateTaskInCsv(int taskId, String newPriority, String newStatus) {
        List<String[]> currentTasks = readAllCsvRows();
        boolean found = false;
        for (String[] task : currentTasks) {
            if (task[0].equals(String.valueOf(taskId))) {
                task[3] = newPriority;
                task[4] = newStatus;
                found = true;
                break;
            }
        }
        if (found) {
            writeAllCsvRows(currentTasks);
            loadTasks();
        } else {
            JOptionPane.showMessageDialog(this, "Task ID " + taskId + " not found for update.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadSelectedTaskForEdit() {
        int selectedRow = priorityTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a task to edit.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (priorityTable.isEditing()) priorityTable.getCellEditor().stopCellEditing();

        editingTaskId = Integer.parseInt((String) tableModel.getValueAt(selectedRow, 0));
        titleField.setText((String) tableModel.getValueAt(selectedRow, 1));

        Date fullDateTime;
        try {
            fullDateTime = DATETIME_FORMAT.parse((String) tableModel.getValueAt(selectedRow, 2));
        } catch (ParseException e) {
            JOptionPane.showMessageDialog(this, "Failed to parse due date for editing: " + tableModel.getValueAt(selectedRow, 2), "Date Parsing Error", JOptionPane.ERROR_MESSAGE);
            throw new RuntimeException("Failed to parse due date string for editing: " + tableModel.getValueAt(selectedRow, 2), e);
        }
        dueDateChooser.setDate(fullDateTime);
        timeInputField.setText(new SimpleDateFormat("HH:mm").format(fullDateTime));

        editTaskButton.setText("Save Changes");
        addTaskButton.setEnabled(false);
        priorityTable.setEnabled(false);
    }

    private void saveEditedTask() {
        String title = titleField.getText().trim();
        Date selectedDate = dueDateChooser.getDate();
        String timeText = timeInputField.getText().trim();

        if (title.isEmpty() || selectedDate == null || !timeText.matches("^(0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]$")) {
            JOptionPane.showMessageDialog(this, "All fields (Title, Due Date, Time) are required and valid.", "Input Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String combinedDateTime = DATE_FORMAT.format(selectedDate) + " " + timeText;

        List<String[]> currentTasks = readAllCsvRows();
        boolean found = false;
        for (String[] task : currentTasks) {
            if (task[0].equals(String.valueOf(editingTaskId))) {
                task[1] = title;
                task[2] = combinedDateTime;
                task[4] = "In process";
                found = true;
                break;
            }
        }

        if (found && writeAllCsvRows(currentTasks)) {
            clearInputFields();
            loadTasks();
            resetEditModeUI();
        } else if (!found) {
            JOptionPane.showMessageDialog(this, "Error: Task ID " + editingTaskId + " not found for editing.", "Error", JOptionPane.ERROR_MESSAGE);
            resetEditModeUI();
        }
    }

    private void exportTasksToCsv() {
       
        // Define the target file directly
        File fileToSave = new File(EXPORT_DIRECTORY, "ExportData.csv");

        try (CSVWriter writer = new CSVWriter(new FileWriter(fileToSave))) {
            writer.writeNext(CSV_HEADERS);

            for (int i = 0; i < tableModel.getRowCount(); i++) {
                String[] rowData = new String[tableModel.getColumnCount()];
                for (int j = 0; j < tableModel.getColumnCount(); j++) {
                    rowData[j] = String.valueOf(tableModel.getValueAt(i, j));
                }
                writer.writeNext(rowData);
            }
            JOptionPane.showMessageDialog(this, "Tasks exported successfully to ExportData folder" , "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error exporting tasks: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void resetEditModeUI() {
        editingTaskId = -1;
        editTaskButton.setText("Edit Task");
        addTaskButton.setEnabled(true);
        priorityTable.setEnabled(true);
        clearInputFields();
    }

    // --- Utility Methods ---

    private void clearInputFields() {
        titleField.setText("");
        dueDateChooser.setDate(new Date());
        timeInputField.setText("13:00");
    }

    private void sortTasksInTable() {
        if (tableModel.getRowCount() == 0) return;

        List<String[]> dataForSorting = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String[] rowData = new String[tableModel.getColumnCount()];
            for (int j = 0; j < tableModel.getColumnCount(); j++) {
                rowData[j] = String.valueOf(tableModel.getValueAt(i, j));
            }
            dataForSorting.add(rowData);
        }

        dataForSorting.sort((task1, task2) -> {
            int priorityOrder1 = getPriorityRank(task1[3]);
            int priorityOrder2 = getPriorityRank(task2[3]);

            if (priorityOrder1 != priorityOrder2) {
                return Integer.compare(priorityOrder2, priorityOrder1);
            }

            Date date1;
            Date date2;
            try {
                date1 = DATETIME_FORMAT.parse(task1[2]);
                date2 = DATETIME_FORMAT.parse(task2[2]);
            } catch (ParseException e) {
                System.err.println("Failed to parse due date strings for sorting. Task1: " + task1[2] + ", Task2: " + task2[2] + " - " + e.getMessage());
                return 0;
            }
            return date1.compareTo(date2);
        });

        tableModel.setRowCount(0);
        dataForSorting.forEach(tableModel::addRow);
    }

    private int getPriorityRank(String priority) {
        int rank = 0;
        switch (priority) {
            case "High":
                rank = 3;
                break;
            case "Medium":
                rank = 2;
                break;
            case "Low":
                rank = 1;
                break;
        }
        return rank;
    }

    private Date addDays(Date date, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DATE, days);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

}