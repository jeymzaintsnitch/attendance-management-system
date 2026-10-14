package OOP;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * ============================================================
 * DSA CONCEPT: HashMap — O(1) average lookup by record ID
 * OOP CONCEPT: Encapsulation, Single Responsibility
 * ============================================================
 * AttendanceCache keeps an in-memory copy of all records so
 * the UI never needs extra database round-trips for sorting,
 * searching, or counting.
 *
 * WHY A CACHE?
 *   Without it, every search/sort would query the DB again.
 *   With a HashMap the lookup by ID is O(1) average.
 *
 * LIFECYCLE:
 *   clear() → put() for each record → use values() / get() → repeat.
 * ============================================================
 */
public class AttendanceCache {

    // Key = record ID string, Value = AttendanceRecord object.
    // HashMap gives O(1) average get/put/containsKey.
    private final Map<String, AttendanceRecord> cache = new HashMap<>();

    /** Inserts or replaces a record. O(1) average. */
    public void put(String id, AttendanceRecord record) {
        cache.put(id, record);
    }

    /** Returns the record for the given ID, or null if absent. O(1) average. */
    public AttendanceRecord get(String id) {
        return cache.get(id);
    }

    /** Removes a record by ID. O(1) average. */
    public void remove(String id) {
        cache.remove(id);
    }

    /** Clears the entire cache — called before every full data reload. */
    public void clear() {
        cache.clear();
    }

    /** Total number of cached records. */
    public int size() {
        return cache.size();
    }

    /** All cached records — used to rebuild the UI list or run statistics. */
    public Collection<AttendanceRecord> values() {
        return cache.values();
    }

    /**
     * Counts records matching both a date and a status.
     * Used by NotificationService to build the today's summary bar.
     * O(n) — only called for display, not in hot paths.
     *
     * @param date   ISO date string (yyyy-MM-dd).
     * @param status "Present", "Absent", or "Late".
     * @return Count of matching records.
     */
    public long countByDateAndStatus(String date, String status) {
        long count = 0;
        for (AttendanceRecord r : cache.values()) {
            if (r.getDate().equals(date) && r.getStatus().equals(status)) {
                count++;
            }
        }
        return count;
    }
}
