package OOP;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * ============================================================
 * OOP CONCEPT: Single Responsibility
 * ============================================================
 * NotificationService ONLY analysis attendance data and raises
 * alerts. It never touches the database or the TableView.
 *
 * FUNCTIONAL REQUIREMENT:
 *   "Notifications for absentees or low attendance"
 *
 * NON-FUNCTIONAL REQUIREMENT:
 *   "Reliable performance" — all calculations read from the
 *   in-memory cache; zero extra DB round-trips.
 *
 * DSA:
 *   checkLowAttendance() uses a TreeMap (Red-Black Tree) to
 *   aggregate per-student stats in alphabetical order.
 *   O(log n) insert, O(n) iteration — ordered output for free.
 * ============================================================
 */
public class NotificationService {

    // Flag a student when their absence rate reaches this fraction.
    // 0.30 = 30% absent triggers a warning.
    private static final double ABSENCE_THRESHOLD = 0.30;

    // Require at least this many records before flagging low attendance,
    // so a single absence on day 1 doesn't trigger a false positive.
    private static final int MIN_RECORDS = 3;

    // Utility class — no instantiation needed.
    private NotificationService() {}

    // =========================================================
    // PUBLIC API
    // =========================================================

    /**
     * Scans the cache for students marked Absent today and shows
     * a warning alert listing their names. Called on every refresh.
     *
     * @param cache    In-memory attendance cache.
     * @param todayStr Today's date in ISO format (yyyy-MM-dd).
     */
    public static void checkTodaysAbsentees(AttendanceCache cache, String todayStr) {
        // Collect names of students absent today — O(n) pass through cache
        List<String> absentNames = new ArrayList<>();
        for (AttendanceRecord r : cache.values()) {
            if (r.getDate().equals(todayStr) && r.getStatus().equals("Absent")) {
                absentNames.add(r.getName());
            }
        }

        // Sort names alphabetically for a readable alert
        absentNames.sort(String.CASE_INSENSITIVE_ORDER);

        if (!absentNames.isEmpty()) {
            showAbsenteeAlert(absentNames, todayStr);
        }
    }

    /**
     * Analyses ALL cached records for students whose overall absence
     * rate is at or above ABSENCE_THRESHOLD, then shows a summary.
     * Triggered by the "Check Attendance" button.
     *
     * DSA: TreeMap keeps student names in alphabetical order (Red-Black Tree).
     *   Each entry: key = student name, value = int[]{totalDays, absentDays}
     *
     * @param cache In-memory attendance cache.
     */
    public static void checkLowAttendance(AttendanceCache cache) {

        // DSA: TreeMap — O(log n) insert, alphabetical iteration for free.
        // int[0] = total records, int[1] = absent count
    	
    	if (cache == null || cache.size() == 0) {
            Alert emptyAlert = new Alert(AlertType.INFORMATION);
            emptyAlert.setTitle("Attendance Check");
            emptyAlert.setHeaderText("No Data Available");
            emptyAlert.setContentText("There are currently no attendance records in the system to analyze.");
            
            
            emptyAlert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            emptyAlert.showAndWait();
            return;
        }
    	
    	
        Map<String, int[]> stats = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        // Single O(n) pass to build per-student totals
        for (AttendanceRecord r : cache.values()) {
            stats.computeIfAbsent(r.getName(), k -> new int[]{0, 0});
            stats.get(r.getName())[0]++;                              // total++
            if (r.getStatus().equals("Absent")) stats.get(r.getName())[1]++; // absent++
        }

        // Build the list of flagged students
        List<String> flagged = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : stats.entrySet()) {
            int total  = entry.getValue()[0];
            int absent = entry.getValue()[1];

            // Skip students with too few records to be meaningful
            if (total < MIN_RECORDS) continue;

            double rate = (double) absent / total;
            if (rate >= ABSENCE_THRESHOLD) {
                flagged.add(String.format("%-30s  Absences: %d / %d  (%.0f%%)",
                        entry.getKey(), absent, total, rate * 100));
            }
        }

        showLowAttendanceAlert(flagged);
    }

    /**
     * Builds a one-line summary string for the status bar at the bottom
     * of the main window, e.g. "Today ▸  Present: 15  |  Late: 3  |  Absent: 2"
     *
     * Uses AttendanceCache.countByDateAndStatus() — no extra DB call.
     *
     * @param cache    In-memory cache.
     * @param todayStr Today's date in ISO format.
     * @return Formatted summary string.
     */
    public static String buildTodaySummary(AttendanceCache cache, String todayStr) {
        long present = cache.countByDateAndStatus(todayStr, "Present");
        long late    = cache.countByDateAndStatus(todayStr, "Late");
        long absent  = cache.countByDateAndStatus(todayStr, "Absent");

        return String.format(
                "Today ▸  Present: %d  |  Late: %d  |  Absent: %d       Total Records: %d",
                present, late, absent, cache.size());
    }

    // =========================================================
    // PRIVATE ALERT BUILDERS
    // =========================================================

    /**
     * Shows a WARNING alert that lists today's absent students.
     * Uses a scrollable TextArea so long lists don't overflow the dialog.
     */
    private static void showAbsenteeAlert(List<String> names, String todayStr) {
        Alert alert = new Alert(AlertType.WARNING);
        alert.setTitle("Absentee Alert");
        alert.setHeaderText("⚠  Students Absent Today (" + todayStr + ")");

        TextArea textArea = new TextArea(String.join("\n", names));
        textArea.setEditable(false);
        textArea.setWrapText(false);
        textArea.setPrefRowCount(Math.min(names.size(), 10)); // cap height at 10 rows

        VBox content = new VBox(textArea);
        VBox.setVgrow(textArea, Priority.ALWAYS);
        alert.getDialogPane().setContent(content);
        alert.getDialogPane().setPrefWidth(400);
        alert.showAndWait();
    }

    /**
     * Shows a WARNING alert listing students with low attendance,
     * or an INFORMATION alert if everyone is in good standing.
     */
    private static void showLowAttendanceAlert(List<String> flagged) {
        if (flagged.isEmpty()) {
            // All students are within the acceptable range
            Alert ok = new Alert(AlertType.INFORMATION, "", ButtonType.OK);
            ok.setTitle("Attendance Check");
            ok.setHeaderText("✓  All students are in good standing");
            ok.setContentText("No student has exceeded the "
                    + (int)(ABSENCE_THRESHOLD * 100) + "% absence threshold.");
            ok.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            ok.showAndWait();
            return;
        }

        Alert alert = new Alert(AlertType.WARNING);
        alert.setTitle("Low Attendance Warning");
        alert.setHeaderText("⚠  Students with ≥ "
                + (int)(ABSENCE_THRESHOLD * 100) + "% absences");

        // Column header + divider line for readability
        String header = String.format("%-30s  %-30s%n%s%n",
                "Student Name", "Absence Rate", "─".repeat(62));

        TextArea textArea = new TextArea(header + String.join("\n", flagged));
        textArea.setEditable(false);
        textArea.setStyle("-fx-font-family: monospace;");
        textArea.setPrefRowCount(Math.min(flagged.size() + 3, 12));

        VBox content = new VBox(textArea);
        VBox.setVgrow(textArea, Priority.ALWAYS);
        alert.getDialogPane().setContent(content);
        alert.getDialogPane().setPrefWidth(500);
        alert.showAndWait();
    }
}
