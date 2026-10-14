package OOP;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================
 * OOP CONCEPTS:
 *   - Inheritance   — extends Application (JavaFX lifecycle)
 *   - Encapsulation — all fields private; accessed only by methods
 *   - Abstraction   — delegates DB/sort/cache/notify to other classes
 *   - Composition   — Main "has-a" AttendanceCache
 *
 * DSA CONCEPTS:
 *   - ObservableList (dynamic array) — bound to TableView
 *   - AttendanceCache (HashMap)      — O(1) lookup after load
 *   - AttendanceSorter (merge sort)  — O(n log n) in-memory sort
 *   - Binary search                  — O(log n) name search
 *
 * FUNCTIONAL REQUIREMENTS:
 *   ✓ Track daily attendance (full CRUD)
 *   ✓ Generate attendance reports (JasperReports viewer + print)
 *   ✓ Notifications for absentees and low attendance
 *
 * NON-FUNCTIONAL REQUIREMENTS:
 *   ✓ Secure  — parameterised SQL in DatabaseManager
 *   ✓ Fast    — sort/search run on the in-memory cache
 *   ✓ Reliable — one DB query per refresh; all else uses the cache
 * ============================================================
 */
public class Main extends Application {

    // ── Table + data ─────────────────────────────────────────────────────────
    private final TableView<AttendanceRecord>      table     = new TableView<>();
    private final ObservableList<AttendanceRecord> tableData = FXCollections.observableArrayList();

    // DSA: HashMap cache — rebuilt once per refresh, reused for all operations
    private final AttendanceCache cache = new AttendanceCache();

    // ── Input fields ─────────────────────────────────────────────────────────
    private TextField        nameInput;
    private DatePicker       dateInput;
    private ComboBox<String> statusInput;

    // ── Search + sort ─────────────────────────────────────────────────────────
    private TextField        searchInput;
    private ComboBox<String> sortComboBox;

    // ── Inline error label (replaces pop-up alerts for validation) ────────────
    private Label inlineErrorLabel;

    // ── Status bar ────────────────────────────────────────────────────────────
    private HBox statusBar;

    // ── Button style constants — shared by styleButton() ─────────────────────
    // Three states: normal (white), hovered (light grey), pressed (darker grey)
    private static final String BTN_NORMAL  =
            "-fx-background-color:#ffffff; -fx-text-fill:#1a1a2e; -fx-font-size:13px;"
            + " -fx-font-weight:bold; -fx-background-radius:6; -fx-border-color:#b0bec5;"
            + " -fx-border-width:1.2; -fx-border-radius:6; -fx-padding:8 14;"
            + " -fx-min-height:38px; -fx-cursor:hand;";

    private static final String BTN_HOVERED =
            "-fx-background-color:#e8eaed; -fx-text-fill:#1a1a2e; -fx-font-size:13px;"
            + " -fx-font-weight:bold; -fx-background-radius:6; -fx-border-color:#78909c;"
            + " -fx-border-width:1.2; -fx-border-radius:6; -fx-padding:8 14;"
            + " -fx-min-height:38px; -fx-cursor:hand;";

    private static final String BTN_PRESSED =
            "-fx-background-color:#d2d5db; -fx-text-fill:#1a1a2e; -fx-font-size:13px;"
            + " -fx-font-weight:bold; -fx-background-radius:6; -fx-border-color:#546e7a;"
            + " -fx-border-width:1.2; -fx-border-radius:6; -fx-padding:8 14;"
            + " -fx-min-height:38px; -fx-cursor:hand;";

    // =========================================================
    // ENTRY POINT
    // =========================================================

    public static void main(String[] args) {
        launch(args);
    }

    // =========================================================
    // JAVAFX LIFECYCLE — start()
    // =========================================================

    /**
     * Builds and shows the main window.
     * Each section is delegated to a focused builder method
     * so start() reads like a clean assembly list.
     */
    @Override
    public void start(Stage stage) {
        DatabaseManager.initializeDatabase();

        setupTable();
        setupInputFields();

        // Assemble the full layout
        VBox root = new VBox(12,
                buildTitle(),
                table,
                buildInputPanel(),
                buildStatusBar()
        );
        root.setPadding(new Insets(20));
        VBox.setVgrow(table, Priority.ALWAYS);

        Scene scene = new Scene(root, 850, 680);
        loadCss(scene);

        // Deselect the table row only when clicking blank/background areas.
        // IMPORTANT: must NOT clear when the user clicks a Button, ComboBox,
        // TextField, or DatePicker. MOUSE_PRESSED fires before onAction, so
        // clearing inputs here would wipe the selected row's data before the
        // button handler can read it (causing the 'name cannot be empty' error).
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (!shouldSuppressClear(e)) {
                table.getSelectionModel().clearSelection();
                clearInputs();
            }
        });

        stage.setTitle("ADKJ — Attendance Management System");
        stage.setScene(scene);
        stage.setMinWidth(950);
        stage.setMinHeight(500);
        stage.show();

        refreshUI();
        // Check today's absentees after the window is fully shown
        Platform.runLater(() ->
                NotificationService.checkTodaysAbsentees(cache, LocalDate.now().toString()));
    }

    // =========================================================
    // LAYOUT BUILDERS — each builds one section of the window
    // =========================================================

    /** Centered, bold title label at the top of the window. */
    private Label buildTitle() {
        Label title = new Label("Attendance Management System");
        title.setFont(Font.font("System", FontWeight.BOLD, 22));
        title.setTextFill(Color.web("#1a237e"));
        title.setMaxWidth(Double.MAX_VALUE); // stretch to full row
        title.setAlignment(Pos.CENTER);      // center the text within it
        return title;
    }

    /**
     * Input panel: [name | status | date]  [search | sort]
     *              [action buttons row]
     *              [inline error label]
     */
    private VBox buildInputPanel() {
        // Inline error label — hidden by default, appears below inputs on error
        inlineErrorLabel = new Label();
        inlineErrorLabel.setStyle("-fx-text-fill:#c62828; -fx-font-size:13px; -fx-font-weight:bold;");
        inlineErrorLabel.setVisible(false);
        inlineErrorLabel.setManaged(false); // takes no layout space when hidden

        // Left side: input fields
        HBox inputRow = new HBox(14, nameInput, statusInput, dateInput);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        // Right side: search + sort
        HBox searchSort = buildSearchSortBar();
        searchSort.setAlignment(Pos.CENTER_RIGHT);

        // Push left group left, right group right
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topBar = new HBox(10, inputRow, spacer, searchSort);
        topBar.setStyle("-fx-background-color:#ffffff; -fx-padding:16;"
                + " -fx-background-radius:8; -fx-border-color:#e2e8f0; -fx-border-radius:8;");

        return new VBox(14, topBar, buildActionButtons(), inlineErrorLabel);
    }

    /** Colour-coded status bar at the bottom showing today's attendance counts. */
    private HBox buildStatusBar() {
        statusBar = new HBox(6);
        statusBar.setStyle("-fx-background-color:#eceff1; -fx-padding:8 14;"
                + " -fx-border-color:#cfd8dc; -fx-border-width:1 0 0 0;");
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setMaxWidth(Double.MAX_VALUE);
        return statusBar;
    }

    // =========================================================
    // TABLE SETUP
    // =========================================================

    /**
     * Configures the TableView: columns, colour-coded status cells,
     * multi-select mode, and the auto-fill listener.
     */
    private void setupTable() {
        // makeColumn() avoids repeating the same 3-line setup for each column
        TableColumn<AttendanceRecord, String> idCol     = makeColumn("ID",           "id",     55,  55, true);
        TableColumn<AttendanceRecord, String> nameCol   = makeColumn("Student Name", "name",  200, 999, false);
        TableColumn<AttendanceRecord, String> dateCol   = makeColumn("Date",         "date",  120, 999, true);
        TableColumn<AttendanceRecord, String> statusCol = makeColumn("Status",       "status",100, 999, true);

        // Status column gets colour-coded text: green / orange / red
        statusCol.setCellFactory(col -> new TableCell<AttendanceRecord, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setText(null); setStyle(""); return; }
                setText(status);
                // Colour scheme: Present = green, Late = dark black, Absent = red
                switch (status) {
                    case "Present": setStyle("-fx-text-fill:#2e7d32; -fx-font-weight:bold;"); break;
                    case "Late":    setStyle("-fx-text-fill:#1a1a1a; -fx-font-weight:bold;"); break; // dark black
                    case "Absent":  setStyle("-fx-text-fill:#c62828; -fx-font-weight:bold;"); break; // red
                    default:        setStyle("");
                }
            }
        });

        table.getColumns().addAll(idCol, nameCol, dateCol, statusCol);
        table.setPlaceholder(new Label("No attendance records found."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Auto-fill inputs when a row is selected
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel == null) return;
            nameInput.setText(sel.getName());
            statusInput.setValue(sel.getStatus());
            try {
                dateInput.setValue(LocalDate.parse(sel.getDate()));
            } catch (Exception e) {
                dateInput.setValue(LocalDate.now()); // edge case: malformed date in DB
            }
        });
    }

    /**
     * Creates a TableColumn with one call instead of 3–4 repeated lines.
     * OOP: Abstraction — hides the repetitive column setup behind a name.
     *
     * @param title    Column header text.
     * @param field    AttendanceRecord getter name (e.g. "name" → getName()).
     * @param minW     Minimum column width in pixels.
     * @param maxW     Maximum column width (use 999 for unconstrained).
     * @param centered true = center-align the cell text.
     */
    private TableColumn<AttendanceRecord, String> makeColumn(
            String title, String field, int minW, int maxW, boolean centered) {

        TableColumn<AttendanceRecord, String> col = new TableColumn<>(title);
        col.setCellValueFactory(new PropertyValueFactory<>(field));
        col.setMinWidth(minW);
        col.setMaxWidth(maxW);
        if (centered) col.setStyle("-fx-alignment: CENTER;");
        return col;
    }

    // =========================================================
    // INPUT FIELDS SETUP
    // =========================================================

    /** Creates the name TextField, DatePicker, and status ComboBox. */
    private void setupInputFields() {
        nameInput = new TextField();
        nameInput.setPromptText("Student Name");
        nameInput.setPrefWidth(220);

        // TextFormatter blocks invalid characters (only letters, spaces, hyphens, apostrophes).
        // Flashes the field red briefly on a rejected keypress.
        nameInput.setTextFormatter(new TextFormatter<>(change -> {
            if (change.getControlNewText().matches("^[a-zA-Z\\s'. -]*$")) {
                nameInput.setStyle("");
                return change; // accept the character
            }
            // Reject — flash red border for 300ms as visual feedback
            nameInput.setStyle("-fx-border-color:#c62828; -fx-border-width:2px;"
                    + " -fx-background-color:#ffebee;");
            PauseTransition flash = new PauseTransition(Duration.millis(300));
            flash.setOnFinished(e -> nameInput.setStyle(""));
            flash.play();
            return null; // block the character
        }));

        dateInput = new DatePicker(LocalDate.now());
        dateInput.setPrefWidth(160);

        statusInput = new ComboBox<>();
        statusInput.getItems().addAll("Present", "Absent", "Late");
        statusInput.setValue("Present");
        statusInput.setPrefWidth(130);
    }

    // =========================================================
    // BUTTON ROW
    // =========================================================

    /**
     * Builds the six action buttons.
     * styleButton() is called once per button via a loop — no repetition.
     * All buttons grow equally across the full row width.
     */
    private HBox buildActionButtons() {
        Button addBtn    = new Button("✚  Mark Attendance");
        Button updateBtn = new Button("✎  Update Selected");
        Button deleteBtn = new Button("✕  Delete Selected");
        Button reportBtn = new Button("⎙  Generate Report");
        Button notifyBtn = new Button("⚠  Check Attendance");
        Button clearBtn  = new Button("✖  Clear");

        for (Button btn : new Button[]{addBtn, updateBtn, deleteBtn, reportBtn, notifyBtn, clearBtn}) {
            styleButton(btn);
        }

        addBtn.setOnAction(e    -> handleAdd());
        updateBtn.setOnAction(e -> handleUpdate());
        deleteBtn.setOnAction(e -> handleDelete());
        reportBtn.setOnAction(e -> ReportGenerator.showReport());
        notifyBtn.setOnAction(e -> NotificationService.checkLowAttendance(cache));
        clearBtn.setOnAction(e  -> clearInputs());

        HBox box = new HBox(8, addBtn, updateBtn, deleteBtn, reportBtn, notifyBtn, clearBtn);
        box.setAlignment(Pos.CENTER_LEFT);
        for (javafx.scene.Node node : box.getChildren()) {
            HBox.setHgrow((Button) node, Priority.ALWAYS);
        }
        return box;
    }

    /**
     * Applies minimalist style + hover/press feedback to a button.
     * Uses the three shared string constants — no duplication per button.
     *
     *   Normal:  white background, dark navy text, soft grey border
     *   Hover:   light grey background — visible feedback, no colour
     *   Pressed: slightly darker grey for click confirmation
     */
    private void styleButton(Button btn) {
        btn.setStyle(BTN_NORMAL);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnMouseEntered(e  -> btn.setStyle(BTN_HOVERED));
        btn.setOnMouseExited(e   -> btn.setStyle(BTN_NORMAL));
        btn.setOnMousePressed(e  -> btn.setStyle(BTN_PRESSED));
        btn.setOnMouseReleased(e -> btn.setStyle(BTN_HOVERED));
    }

    // =========================================================
    // SEARCH + SORT BAR
    // =========================================================

    private HBox buildSearchSortBar() {
        searchInput = new TextField();
        searchInput.setPromptText("🔍  Search by student name...");
        searchInput.setPrefWidth(240);
        // Live search — fires on every keystroke
        searchInput.textProperty().addListener((obs, old, text) -> handleSearch(text));

        Button clearSearch = new Button("Clear");
        clearSearch.setOnAction(e -> { searchInput.clear(); rebuildTable(null); });

        sortComboBox = new ComboBox<>();
        sortComboBox.getItems().addAll("Date (Newest First)", "Name (A→Z)", "Status");
        sortComboBox.setValue("Date (Newest First)");
        sortComboBox.setOnAction(e -> handleSort());

        HBox box = new HBox(8, searchInput, clearSearch,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                new Label("Sort by:"), sortComboBox);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    // =========================================================
    // EVENT HANDLERS
    // =========================================================

    /** Validates inputs, checks for duplicate date, then inserts a new record. */
    private void handleAdd() {
        String name   = nameInput.getText().trim();
        String date   = getInputDate();
        String status = statusInput.getValue();

        String error = DatabaseManager.validateInput(name, date, status);
        if (error != null) { showInlineError(error); return; }

        // Duplicate date check — warn before adding a second entry for the same student+date
        for (AttendanceRecord r : cache.values()) {
            if (r.getName().equalsIgnoreCase(name) && r.getDate().equals(date)) {
                if (!confirm("Please check the date before proceeding.\n\n"
                        + name + " already has a record on " + date
                        + " (" + r.getStatus() + ").\n\nDo you still want to add another entry?")) {
                    return;
                }
                break;
            }
        }

        if (DatabaseManager.addRecord(name, date, status)) {
            refreshUI();
        } else {
            showInlineError("Failed to add record. Check the console for details.");
        }
    }

    /** Validates selection and inputs, confirms, then updates the selected record. */
    private void handleUpdate() {
        ObservableList<AttendanceRecord> selected = table.getSelectionModel().getSelectedItems();

        if (selected == null || selected.isEmpty()) { showInlineError("Select a record to update."); return; }
        if (selected.size() > 1)                   { showInlineError("Select only ONE record to update."); return; }

        AttendanceRecord sel  = selected.get(0);
        String name   = nameInput.getText().trim();
        String date   = getInputDate();
        String status = statusInput.getValue();

        String error = DatabaseManager.validateInput(name, date, status);
        if (error != null) { showInlineError(error); return; }

        // Confirm is a dialog here — it is a deliberate action, not a validation error
        if (!confirm("Update record for \"" + sel.getName() + "\"?")) return;

        if (DatabaseManager.updateRecord(sel.getId(), name, date, status)) {
            refreshUI();
        } else {
            showInlineError("Update failed. The record may no longer exist.");
        }
    }

    /** Confirms, then deletes all selected records. Supports multi-select delete. */
    private void handleDelete() {
        ObservableList<AttendanceRecord> selected = table.getSelectionModel().getSelectedItems();

        if (selected == null || selected.isEmpty()) { showInlineError("Select one or more records to delete."); return; }

        int count = selected.size();
        String msg = (count == 1)
                ? "Delete record for \"" + selected.get(0).getName() + "\"?\nThis cannot be undone."
                : "Delete all " + count + " selected records?\nThis cannot be undone.";

        if (!confirm(msg)) return;

        // Copy to a new list first — modifying 'selected' mid-loop would cause ConcurrentModificationException
        boolean anyFailed = false;
        for (AttendanceRecord r : new ArrayList<>(selected)) {
            if (!DatabaseManager.deleteRecord(r.getId())) anyFailed = true;
        }

        refreshUI();
        if (anyFailed) showInlineError("Some records could not be deleted. They may no longer exist.");
    }

    /**
     * Filters the TableView using binary search on a name-sorted list.
     * DSA: sort by name O(n log n), then binary search O(log n).
     */
    private void handleSearch(String query) {
        if (query == null || query.trim().isEmpty()) { rebuildTable(null); return; }

        // Sort a copy of the cache by name — required for binary search
        List<AttendanceRecord> byName = new ArrayList<>(cache.values());
        AttendanceSorter.sortByNameAsc(byName);

        int start = AttendanceSorter.binarySearchByName(byName, query.trim());
        if (start == -1) {
            tableData.setAll(new ArrayList<>());
            table.setPlaceholder(new Label("No records found for \"" + query.trim() + "\"."));
            return;
        }

        // Collect all consecutive records that start with the query prefix
        String lq = query.trim().toLowerCase();
        List<AttendanceRecord> matches = new ArrayList<>();
        for (int i = start; i < byName.size(); i++) {
            if (byName.get(i).getName().toLowerCase().startsWith(lq)) matches.add(byName.get(i));
            else break; // list is sorted — no more matches past this point
        }
        tableData.setAll(matches);
    }

    /** Sorts the cached list with merge sort and updates the TableView — no DB query. */
    private void handleSort() {
        List<AttendanceRecord> list = new ArrayList<>(cache.values());
        switch (sortComboBox.getValue()) {
            case "Date (Newest First)": AttendanceSorter.sortByDateDesc(list); break;
            case "Name (A→Z)":         AttendanceSorter.sortByNameAsc(list);  break;
            case "Status":             AttendanceSorter.sortByStatus(list);   break;
            default:                   AttendanceSorter.sortByDateDesc(list);
        }
        tableData.setAll(list);
    }

    // =========================================================
    // DATA REFRESH
    // =========================================================

    /**
     * Master refresh — one DB query, then cache + sort + UI update.
     *
     * PERFORMANCE: Only ONE database call (getAllRecords) per refresh.
     * All sort/search/count operations use the in-memory cache after this.
     */
    private void refreshUI() {
        List<AttendanceRecord> records = DatabaseManager.getAllRecords();

        // Rebuild HashMap cache — O(n)
        cache.clear();
        for (AttendanceRecord r : records) cache.put(r.getId(), r);

        // Sort newest-first and push to the TableView's ObservableList
        AttendanceSorter.sortByDateDesc(records);
        tableData.setAll(records);
        table.setItems(tableData);
        table.setPlaceholder(new Label("No attendance records found."));

        clearInputs();
        updateStatusBar(LocalDate.now().toString());
    }

    /** Rebuilds the TableView from the cache with an optional comparator. */
    private void rebuildTable(java.util.Comparator<AttendanceRecord> comp) {
        List<AttendanceRecord> list = new ArrayList<>(cache.values());
        AttendanceSorter.sort(list, comp); // null → natural order (date desc)
        tableData.setAll(list);
        table.setPlaceholder(new Label("No attendance records found."));
    }

    /** Resets all input fields to defaults and hides the inline error. */
    private void clearInputs() {
        nameInput.clear();
        dateInput.setValue(LocalDate.now());
        statusInput.setValue("Present");
        table.getSelectionModel().clearSelection();
        inlineErrorLabel.setVisible(false);
        inlineErrorLabel.setManaged(false);
    }

    // =========================================================
    // STATUS BAR
    // =========================================================

    /**
     *   Present       = green      (#2e7d32)
     *   Late          = dark black (#1a1a1a)
     *   Absent        = red        (#c62828)
     *   Today ▸       = dark black (#1a1a1a) — neutral
     *   Separators |  = light grey (#9e9e9e) — neutral
     *   Total Records = dark black (#1a1a1a) — neutral
     *
     * DSA: reads from AttendanceCache — O(n) count, no extra DB call.
     *
     * @param todayStr Today's date in ISO format (yyyy-MM-dd).
     */
    private void updateStatusBar(String todayStr) {
        long present = cache.countByDateAndStatus(todayStr, "Present");
        long late    = cache.countByDateAndStatus(todayStr, "Late");
        long absent  = cache.countByDateAndStatus(todayStr, "Absent");

        // Colour constants — defined once, named clearly
        final String GREEN = "#2e7d32";  // Present — green
        final String DARK  = "#1a1a1a";  // Late / neutral labels — dark black
        final String RED   = "#c62828";  // Absent  — red
        final String GREY  = "#9e9e9e";  // Separators

        statusBar.getChildren().setAll(
            barLabel("Today ▸",                    DARK),  // prefix  — neutral
            barLabel("Present: "       + present,  GREEN), // Present — green
            barLabel("|",                          GREY),  // separator
            barLabel("Late: "          + late,     DARK),  // Late    — dark black
            barLabel("|",                          GREY),  // separator
            barLabel("Absent: "        + absent,   RED),   // Absent  — red
            barLabel("|",                          GREY),  // separator
            barLabel("Total Records: " + cache.size(), DARK) // Total — neutral
        );
    }

    /**
     * REUSABLE HELPER — creates one styled Label segment for the status bar.
     *
     * OOP: Encapsulation — font size and weight live here only;
     *      any future style change needs editing in one place.
     * DRY: Eliminates repeated new Label() + setStyle() calls.
     *
     * @param text  Display text of the label.
     * @param color CSS hex colour string, e.g. "#2e7d32".
     * @return      A fully styled JavaFX Label.
     */
    private Label barLabel(String text, String color) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size:13; -fx-font-weight:bold; -fx-text-fill:" + color + ";");
        return lbl;
    }

    // =========================================================
    // HELPERS
    // =========================================================

    /**
     * Shows a non-blocking inline error below the input row.
     * Automatically hides after 4 seconds — no user action required.
     */
    private void showInlineError(String message) {
        inlineErrorLabel.setText("⚠  " + message);
        inlineErrorLabel.setVisible(true);
        inlineErrorLabel.setManaged(true);

        PauseTransition hide = new PauseTransition(Duration.seconds(4));
        hide.setOnFinished(e -> {
            inlineErrorLabel.setVisible(false);
            inlineErrorLabel.setManaged(false);
        });
        hide.play();
    }

    /**
     * Shows a YES / NO confirmation dialog.
     * Used for Update and Delete — deliberate actions that need explicit consent.
     *
     * @return true if the user clicked YES.
     */
    private boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setTitle("Confirm");
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    /**
     * Returns today's date from the DatePicker as an ISO string (yyyy-MM-dd).
     * Falls back to today if the picker is empty.
     * Extracted as a helper because handleAdd() and handleUpdate() both need it.
     */
    private String getInputDate() {
        return dateInput.getValue() != null
                ? dateInput.getValue().toString()
                : LocalDate.now().toString();
    }

    /**
     * Returns true when the clear-on-click behaviour should be suppressed.
     * Suppressed for: the TableView itself, and any interactive control
     * (Button, ComboBox, TextField, DatePicker) so that clicking an action
     * button while a row is selected does NOT wipe the inputs before the
     * button's onAction handler runs.
     *
     * OOP: Single-responsibility — all 'should I clear?' logic lives here.
     * DRY: One walk up the scene graph covers every interactive node type.
     */
    private boolean shouldSuppressClear(MouseEvent event) {
        javafx.scene.Node node = (javafx.scene.Node) event.getTarget();
        while (node != null) {
            if (node == table)                       return true; // inside TableView
            if (node instanceof Button)              return true; // action buttons
            if (node instanceof ComboBox)            return true; // status / sort dropdowns
            if (node instanceof TextField)           return true; // name / search inputs
            if (node instanceof DatePicker)          return true; // date picker
            if (node instanceof javafx.scene.control.Separator) return true; // search-bar separator
            node = node.getParent();
        }
        return false;
    }

    /** Safely loads style.css from the classpath root. Falls back silently if missing. */
    private void loadCss(Scene scene) {
        URL cssUrl = getClass().getResource("/style.css");
        if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
    }
}
