package OOP;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.view.JasperViewer;

import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.sql.Connection;
import java.util.HashMap;

/**
 * ============================================================
 * OOP CONCEPTS:
 *   - Single Responsibility — only handles JasperReports logic
 *   - Encapsulation         — compileReport() is private
 *   - Static utility        — no instance state needed
 *
 * FUNCTIONAL REQUIREMENT:
 *   "Generate attendance reports for individuals (screen and print)"
 *   JasperViewer provides both on-screen view and a print button.
 *
 * ERROR HANDLING:
 *   - Null-checks the DB connection before use.
 *   - finally block always closes the connection (resource safety).
 *   - Separate catch for JRException vs generic Exception.
 * ============================================================
 */
public class ReportGenerator {

    // Place AttendanceReport.jrxml inside src/OOP/ in Eclipse.
    // Right-click the file → Build Path → Exclude from exclusion filters
    // so Eclipse copies it to the bin/ output directory at compile time.
    private static final String JRXML_PATH = "src/OOP/AttendanceReport.jrxml";

    // Utility class — no instantiation.
    private ReportGenerator() {}

    /**
     * Full report pipeline: compile → connect → fill → view.
     *
     * STEP 1: Compile the JRXML template into a JasperReport object.
     * STEP 2: Open a DB connection (JasperFillManager needs it to run the SQL).
     * STEP 3: Fill the report — JasperFillManager executes the query from
     *         the JRXML and produces a JasperPrint (rendered page model).
     * STEP 4: Open JasperViewer with that JasperPrint. The viewer has its
     *         own copy of the data at this point, so we can close the DB.
     *
     * The second argument to viewReport(jasperPrint, false) means:
     *   false = do NOT exit the JVM when the viewer window closes.
     */
    public static void showReport() {
        Connection conn = null;

        try {
            // STEP 1 — Compile JRXML
            System.out.println("[Report] Compiling template...");
            JasperReport jasperReport = compileReport();
            if (jasperReport == null) return; // compileReport() already logged the error

            // STEP 2 — Connect to DB
            System.out.println("[Report] Connecting to database...");
            conn = DatabaseManager.connect();
            if (conn == null) {
                // Graceful degradation — show a user-facing dialog instead of crashing
                showErrorAlert("Cannot connect to the database.\nEnsure attendance.db exists.");
                return;
            }

            // STEP 3 — Fill report with live data
            // Parameters map is empty here; add entries (e.g. date range) in future.
            System.out.println("[Report] Filling report with data...");
            JasperPrint jasperPrint = JasperFillManager.fillReport(
                    jasperReport, new HashMap<>(), conn);

            // STEP 4 — Open the viewer window
            System.out.println("[Report] Opening viewer...");
            JasperViewer.viewReport(jasperPrint, false); // false = keep main window alive

        } catch (JRException e) {
            // JasperReports-specific errors: bad JRXML, fill failure, viewer error
            System.err.println("[Report] JasperReports error: " + e.getMessage());
            e.printStackTrace();
            showErrorAlert("Report generation failed:\n" + e.getMessage());

        } catch (Exception e) {
            // Catch-all for any unexpected runtime error
            System.err.println("[Report] Unexpected error: " + e.getMessage());
            e.printStackTrace();
            showErrorAlert("An unexpected error occurred:\n" + e.getMessage());

        } finally {
            // Always close the connection — JasperPrint no longer needs it after fill
            if (conn != null) {
                try {
                    conn.close();
                } catch (Exception e) {
                    System.err.println("[Report] Could not close connection: " + e.getMessage());
                }
            }
        }
    }

    // =========================================================
    // PRIVATE HELPERS
    // =========================================================

    /**
     * Compiles the JRXML file to a JasperReport object.
     * Returns null and logs the error if compilation fails
     * (e.g. file missing, bad XML), so showReport() can exit cleanly.
     */
    private static JasperReport compileReport() {
        try {
            return JasperCompileManager.compileReport(JRXML_PATH);
        } catch (JRException e) {
            System.err.println("[Report] Compile error: " + e.getMessage());
            System.err.println("  → Check that '" + JRXML_PATH + "' exists and is valid.");
            showErrorAlert("Could not compile the report template.\n"
                    + "Ensure AttendanceReport.jrxml is in src/OOP/");
            return null;
        }
    }

    /**
     * Shows an ERROR alert on the JavaFX Application Thread.
     * Platform.runLater() is required if this is called from a non-FX thread.
     */
    private static void showErrorAlert(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Report Error");
            alert.setHeaderText("Could Not Generate Report");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}
