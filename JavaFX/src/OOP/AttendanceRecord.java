package OOP;

import javafx.beans.property.SimpleStringProperty;

/**
 * ============================================================
 * OOP CONCEPT: Encapsulation + JavaFX Observable Properties
 * ============================================================
 * AttendanceRecord models a single row in the 'records' table.
 *
 * - All fields are PRIVATE (encapsulation).
 * - SimpleStringProperty lets the TableView auto-update on changes.
 * - Implements Comparable so AttendanceSorter can sort by date.
 *
 * DSA: This is the base element used in:
 *   - ObservableList (dynamic array bound to TableView)
 *   - AttendanceCache  (HashMap — O(1) lookup by ID)
 *   - AttendanceSorter (merge sort + binary search)
 * ============================================================
 */
public class AttendanceRecord implements Comparable<AttendanceRecord> {

    // --- PRIVATE FIELDS (Encapsulation) ---
    // SimpleStringProperty lets JavaFX TableView observe value changes.
    private final SimpleStringProperty id;
    private final SimpleStringProperty name;
    private final SimpleStringProperty date;
    private final SimpleStringProperty status;

    /**
     * Creates a fully-initialised attendance record.
     *
     * @param id     Database primary key (as String).
     * @param name   Student's full name.
     * @param date   Attendance date in ISO format (yyyy-MM-dd).
     * @param status One of: "Present", "Absent", "Late".
     */
    public AttendanceRecord(String id, String name, String date, String status) {
        this.id     = new SimpleStringProperty(id);
        this.name   = new SimpleStringProperty(name);
        this.date   = new SimpleStringProperty(date);
        this.status = new SimpleStringProperty(status);
    }

    // --- GETTERS (OOP encapsulation — fields accessed through methods) ---
    public String getId()     { return id.get(); }
    public String getName()   { return name.get(); }
    public String getDate()   { return date.get(); }
    public String getStatus() { return status.get(); }

    // --- JAVAFX PROPERTY ACCESSORS (required by PropertyValueFactory) ---
    public SimpleStringProperty idProperty()     { return id; }
    public SimpleStringProperty nameProperty()   { return name; }
    public SimpleStringProperty dateProperty()   { return date; }
    public SimpleStringProperty statusProperty() { return status; }

    /**
     * DSA: Natural ordering by DATE descending (newest first).
     * Used by AttendanceSorter's merge sort as the default comparator.
     */
    @Override
    public int compareTo(AttendanceRecord other) {
        return other.getDate().compareTo(this.getDate()); // reverse = newest first
    }

    /** Two records are equal when they share the same database ID. */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AttendanceRecord)) return false;
        return this.getId().equals(((AttendanceRecord) obj).getId());
    }

    @Override
    public int hashCode() { return getId().hashCode(); }

    @Override
    public String toString() {
        return String.format("AttendanceRecord[id=%s, name=%s, date=%s, status=%s]",
                getId(), getName(), getDate(), getStatus());
    }
}
