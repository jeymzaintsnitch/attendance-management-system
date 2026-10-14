package OOP;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================
 * OOP CONCEPTS:
 *   - Single Responsibility — only handles DB I/O
 *   - Encapsulation         — URL and helpers are private
 *   - Static utility        — no instance needed
 *
 * NON-FUNCTIONAL REQUIREMENTS:
 *   - "Secure storage" — PreparedStatement prevents SQL injection.
 *   - "Reliable performance" — try-with-resources closes every
 *     Connection/Statement/ResultSet automatically; no leaks.
 *
 * DSA:
 *   - getAllRecords() returns a mutable ArrayList so AttendanceSorter
 *     can sort it in-place without an extra copy.
 * ============================================================
 */
public class DatabaseManager {

    // SQLite file is created in the project root on first run.
    private static final String URL = "jdbc:sqlite:attendance.db";

    // =========================================================
    // CONNECTION
    // =========================================================

    /**
     * Opens a fresh JDBC connection to the SQLite file.
     * Returns null (and logs) if the connection fails.
     *
     * NOTE: SQLite is a local file — no network = no external exposure.
     */
    public static Connection connect() {
        try {
            return DriverManager.getConnection(URL);
        } catch (SQLException e) {
            System.err.println("[DB] Connection failed: " + e.getMessage());
            return null;
        }
    }

    // =========================================================
    // SCHEMA INIT
    // =========================================================

    /**
     * Creates the 'records' table and a date index if they don't exist.
     * Safe to call every time the app starts (idempotent).
     *
     * Schema notes:
     *   id           — auto-increment surrogate key
     *   student_name — mandatory, max enforced in validateInput()
     *   date         — ISO-8601 TEXT (yyyy-MM-dd), sortable as string
     *   status       — CHECK constraint enforces domain at DB level
     *
     * The date index speeds up date-filtered queries (e.g. today's absentees).
     */
    public static void initializeDatabase() {
        String createTable =
            "CREATE TABLE IF NOT EXISTS records ("
            + "  id           INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "  student_name TEXT    NOT NULL,"
            + "  date         TEXT    NOT NULL,"
            + "  status       TEXT    NOT NULL"
            + "    CHECK(status IN ('Present','Absent','Late'))"
            + ");";

        String createIndex =
            "CREATE INDEX IF NOT EXISTS idx_date ON records(date);";

        try (Connection conn = connect();
             Statement stmt  = conn.createStatement()) {

            stmt.execute(createTable);
            stmt.execute(createIndex);
            System.out.println("[DB] Initialised successfully.");

        } catch (SQLException e) {
            System.err.println("[DB] Init error: " + e.getMessage());
        }
    }

    // =========================================================
    // INPUT VALIDATION — shared by addRecord() and updateRecord()
    // =========================================================

    /**
     * Validates all three user-supplied fields before any DB call.
     * Centralising validation here means the UI never sends bad data.
     *
     * @param name   Student name (non-empty, ≤ 100 chars)
     * @param date   Date string  (must parse as a valid LocalDate)
     * @param status Status       (must be Present / Absent / Late)
     * @return Human-readable error message, or null if everything is valid.
     */
    public static String validateInput(String name, String date, String status) {

        // --- Name ---
        if (name == null || name.trim().isEmpty())
            return "Student name cannot be empty.";
        if (name.trim().length() > 100)
            return "Student name cannot exceed 100 characters.";
        
     // REGEX: Allows English letters, spaces, hyphens, and apostrophes
        String namePattern = "^[a-zA-Z\\s'. -]+$";
        if (!name.trim().matches(namePattern)) {
            return "Student name can only contain letters, spaces, hyphens (-), and apostrophes (').";
        }

        // --- Date ---
        if (date == null || date.trim().isEmpty())
            return "Date cannot be empty.";
        try {
            LocalDate.parse(date); // throws if the string is not a valid date
        } catch (DateTimeParseException e) {
            return "Date must be in YYYY-MM-DD format.";
        }

        // --- Status ---
        if (!status.equals("Present") && !status.equals("Absent") && !status.equals("Late"))
            return "Status must be Present, Absent, or Late.";

        return null; // null = valid
    }

    // =========================================================
    // [C]REATE
    // =========================================================

    /**
     * Inserts a new attendance record.
     *
     * SECURITY: PreparedStatement — user input is never concatenated
     * into SQL strings, preventing SQL injection attacks.
     *
     * EDGE CASE: Duplicate (same student + same date) is intentionally
     * allowed so AM/PM sessions can both be recorded if needed.
     *
     * @return true on success, false on validation failure or DB error.
     */
    public static boolean addRecord(String name, String date, String status) {
        String error = validateInput(name, date, status);
        if (error != null) {
            System.err.println("[DB] Add validation: " + error);
            return false;
        }

        String sql = "INSERT INTO records(student_name, date, status) VALUES(?,?,?)";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name.trim());
            ps.setString(2, date);
            ps.setString(3, status);
            ps.executeUpdate();
            return true;

        } catch (SQLException e) {
            System.err.println("[DB] Insert error: " + e.getMessage());
            return false;
        }
    }

    // =========================================================
    // [R]EAD
    // =========================================================

    /**
     * Fetches every record from the DB into an ArrayList.
     *
     * DSA: Returns a mutable ArrayList so AttendanceSorter can sort
     * it in-place. The same list is used to rebuild AttendanceCache.
     *
     * ORDER: raw insertion order; AttendanceSorter applies the
     * chosen sort order after this call returns.
     *
     * @return All records, or an empty list on error (never null).
     */
    public static List<AttendanceRecord> getAllRecords() {
        List<AttendanceRecord> records = new ArrayList<>();
        String sql = "SELECT id, student_name, date, status FROM records";

        try (Connection conn = connect();
             Statement stmt  = conn.createStatement();
             ResultSet rs    = stmt.executeQuery(sql)) {

            while (rs.next()) {
                records.add(new AttendanceRecord(
                        String.valueOf(rs.getInt("id")),
                        rs.getString("student_name"),
                        rs.getString("date"),
                        rs.getString("status")
                ));
            }

        } catch (SQLException e) {
            System.err.println("[DB] Read error: " + e.getMessage());
        }
        return records;
    }

    // =========================================================
    // [U]PDATE
    // =========================================================

    /**
     * Updates an existing record by its primary key.
     *
     * EDGE CASE: If the record was deleted between the UI selection
     * and this call, rowsAffected == 0 and we return false so the
     * caller can show a friendly "record no longer exists" message.
     *
     * @param id     String form of the integer PK.
     * @return true if exactly one row was changed.
     */
    public static boolean updateRecord(String id, String name, String date, String status) {
        // Parse and validate the ID first — it must be a positive integer
        int recordId;
        try {
            recordId = Integer.parseInt(id);
        } catch (NumberFormatException e) {
            System.err.println("[DB] Update: invalid ID '" + id + "'");
            return false;
        }

        String error = validateInput(name, date, status);
        if (error != null) {
            System.err.println("[DB] Update validation: " + error);
            return false;
        }

        String sql = "UPDATE records SET student_name=?, date=?, status=? WHERE id=?";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name.trim());
            ps.setString(2, date);
            ps.setString(3, status);
            ps.setInt(4, recordId);
            return ps.executeUpdate() > 0; // false if the row no longer exists

        } catch (SQLException e) {
            System.err.println("[DB] Update error: " + e.getMessage());
            return false;
        }
    }

    // =========================================================
    // [D]ELETE
    // =========================================================

    /**
     * Deletes the record with the given primary key.
     *
     * EDGE CASE: Deleting a non-existent ID returns false gracefully
     * instead of throwing an exception.
     *
     * @param id String form of the integer PK.
     * @return true if one row was removed.
     */
    public static boolean deleteRecord(String id) {
        int recordId;
        try {
            recordId = Integer.parseInt(id);
        } catch (NumberFormatException e) {
            System.err.println("[DB] Delete: invalid ID '" + id + "'");
            return false;
        }

        String sql = "DELETE FROM records WHERE id=?";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, recordId);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("[DB] Delete error: " + e.getMessage());
            return false;
        }
    }
}
