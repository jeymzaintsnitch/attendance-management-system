package OOP;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ============================================================
 * DSA CONCEPTS:
 *   1. MERGE SORT   — O(n log n) guaranteed stable sort
 *   2. BINARY SEARCH — O(log n) name search on a sorted list
 * OOP CONCEPTS:
 *   - Utility class  — static methods only, no instantiation
 *   - Abstraction    — callers pick a sort mode; algorithm is hidden
 *   - Polymorphism   — sort() accepts any Comparator<AttendanceRecord>
 * ============================================================
 *
 * WHY MERGE SORT?
 *   Guaranteed O(n log n) worst-case (unlike quicksort).
 *   STABLE — equal keys preserve original relative order,
 *   important when sorting by date and names are the same.
 *
 * WHY BINARY SEARCH?
 *   After sorting by name, finding a student is O(log n)
 *   instead of O(n) linear scan. Pre-condition: list must be
 *   sorted by name before calling binarySearchByName().
 * ============================================================
 */
public class AttendanceSorter {

    // Prevent instantiation — this is a utility class.
    private AttendanceSorter() {}

    // =========================================================
    // PUBLIC SORT METHODS
    // =========================================================

    /**
     * General-purpose sort using merge sort.
     * Passing null for comparator falls back to natural order (date desc).
     *
     * @param list       List to sort in-place.
     * @param comparator Sort key; null = AttendanceRecord.compareTo (date desc).
     */
    public static void sort(List<AttendanceRecord> list, Comparator<AttendanceRecord> comparator) {
        if (list == null || list.size() <= 1) return; // nothing to sort

        Comparator<AttendanceRecord> comp =
                (comparator != null) ? comparator : Comparator.naturalOrder();

        mergeSort(list, 0, list.size() - 1, comp);
    }

    /** Sorts by date, newest first (default view order). */
    public static void sortByDateDesc(List<AttendanceRecord> list) {
        sort(list, Comparator.naturalOrder());
    }

    /** Sorts alphabetically by student name, A → Z. */
    public static void sortByNameAsc(List<AttendanceRecord> list) {
        sort(list, Comparator.comparing(AttendanceRecord::getName,
                                        String.CASE_INSENSITIVE_ORDER));
    }

    /** Sorts by status: Present → Late → Absent. */
    public static void sortByStatus(List<AttendanceRecord> list) {
        sort(list, Comparator.comparingInt(r -> statusRank(r.getStatus())));
    }

    // =========================================================
    // MERGE SORT — private implementation
    // =========================================================

    /**
     * Recursively splits the subarray [left..right] into halves,
     * sorts each half, then merges them back.
     *
     * Time:  O(n log n) — splits log n times, each merge is O(n).
     * Space: O(n)       — temporary arrays in the merge step.
     */
    private static void mergeSort(List<AttendanceRecord> list,
                                   int left, int right,
                                   Comparator<AttendanceRecord> comp) {
        if (left >= right) return; // base case: 0 or 1 element

        int mid = left + (right - left) / 2; // safe midpoint (avoids int overflow)

        mergeSort(list, left, mid, comp);       // sort left half
        mergeSort(list, mid + 1, right, comp);  // sort right half
        merge(list, left, mid, right, comp);    // combine
    }

    /**
     * Merges two already-sorted subarrays [left..mid] and [mid+1..right]
     * back into the original list in sorted order.
     *
     * STABLE: when elements are equal the left-half element wins,
     * preserving the original relative order.
     */
    private static void merge(List<AttendanceRecord> list,
                               int left, int mid, int right,
                               Comparator<AttendanceRecord> comp) {

        // Copy both halves into temporary buffers
        List<AttendanceRecord> leftHalf  = new ArrayList<>(list.subList(left, mid + 1));
        List<AttendanceRecord> rightHalf = new ArrayList<>(list.subList(mid + 1, right + 1));

        int i = 0, j = 0, k = left; // i = left pointer, j = right pointer, k = write position

        // Pick the smaller front element and write it back
        while (i < leftHalf.size() && j < rightHalf.size()) {
            if (comp.compare(leftHalf.get(i), rightHalf.get(j)) <= 0) {
                list.set(k++, leftHalf.get(i++)); // left wins (or tie — stable)
            } else {
                list.set(k++, rightHalf.get(j++));
            }
        }

        // Drain whichever half still has elements
        while (i < leftHalf.size())  list.set(k++, leftHalf.get(i++));
        while (j < rightHalf.size()) list.set(k++, rightHalf.get(j++));
    }

    // =========================================================
    // BINARY SEARCH — O(log n) name lookup
    // =========================================================

    /**
     * Finds the first record whose name starts with targetName
     * in a list that was already sorted by name ascending.
     *
     * PRE-CONDITION: list must be sorted with sortByNameAsc() first.
     *
     * Time:  O(log n) — halves the search space each iteration.
     * Space: O(1)     — iterative, no recursion stack.
     *
     * @param sortedList Name-ascending sorted list.
     * @param targetName Prefix to look for (case-insensitive).
     * @return Index of first match, or -1 if not found.
     */
    public static int binarySearchByName(List<AttendanceRecord> sortedList, String targetName) {
        if (sortedList == null || sortedList.isEmpty() || targetName == null) return -1;

        String target = targetName.trim().toLowerCase();
        int low = 0, high = sortedList.size() - 1;

        while (low <= high) {
            int mid = low + (high - low) / 2;
            String midName = sortedList.get(mid).getName().toLowerCase();

            if (midName.startsWith(target)) {
                // Found a match — scan backward to the FIRST matching entry
                while (mid > 0
                        && sortedList.get(mid - 1).getName().toLowerCase().startsWith(target)) {
                    mid--;
                }
                return mid;

            } else if (midName.compareTo(target) < 0) {
                low = mid + 1;  // target is to the right

            } else {
                high = mid - 1; // target is to the left
            }
        }

        return -1; // not found
    }

    // =========================================================
    // HELPER
    // =========================================================

    /**
     * Maps a status string to an integer for custom sort order.
     * Present (0) → Late (1) → Absent (2) — most positive first.
     */
    private static int statusRank(String status) {
        switch (status) {
            case "Present": return 0;
            case "Late":    return 1;
            case "Absent":  return 2;
            default:        return 3; // unknown values go last
        }
    }
}
