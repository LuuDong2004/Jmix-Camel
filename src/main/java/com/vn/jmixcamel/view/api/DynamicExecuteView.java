package com.vn.jmixcamel.view.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.editor.Editor;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.Route;
import com.vn.jmixcamel.dto.ApiConfig;
import com.vn.jmixcamel.dto.DbQueryConfig;
import com.vn.jmixcamel.dto.ExecutionConfig;
import com.vn.jmixcamel.dto.QueryFilter;
import com.vn.jmixcamel.service.CamelDslEmitter;
import com.vn.jmixcamel.service.DynamicExecutionService;
import com.vn.jmixcamel.service.query.QueryableEntityRegistry;
import com.vn.jmixcamel.service.query.SqlQueryParser;
import com.vn.jmixcamel.view.main.MainView;
import io.jmix.flowui.component.combobox.JmixComboBox;
import io.jmix.flowui.component.textarea.JmixTextArea;
import io.jmix.flowui.component.textfield.JmixIntegerField;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Route(value = "dynamic-execute-view", layout = MainView.class)
@ViewController(id = "DynamicExecuteView")
@ViewDescriptor(path = "dynamic-execute-view.xml")
public class DynamicExecuteView extends StandardView {

    private static final List<String> HTTP_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final List<String> ORDER_DIRS = List.of("ASC", "DESC");
    private static final List<String> FILTER_OPS = List.of("=", "!=", ">", ">=", "<", "<=", "like");

    private final ObjectMapper yamlMapper = new ObjectMapper(
            new YAMLFactory()
                    .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                    .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES))
            .findAndRegisterModules();

    @ViewComponent private JmixComboBox<String> apiMethodField;
    @ViewComponent private TypedTextField<String> apiUrlField;

    @ViewComponent private JmixComboBox<String> dbEntityField;
    @ViewComponent private TypedTextField<String> dbOrderByField;
    @ViewComponent private JmixComboBox<String> dbOrderDirField;
    @ViewComponent private JmixIntegerField dbLimitField;

    @ViewComponent private JmixTextArea previewArea;
    @ViewComponent private JmixTextArea resultArea;

    @ViewComponent private JmixButton editInputBtn;
    @ViewComponent private JmixButton editHeadersBtn;
    @ViewComponent private JmixButton editBodyBtn;
    @ViewComponent private JmixButton editExtractBtn;
    @ViewComponent private JmixButton editFiltersBtn;
    @ViewComponent private JmixButton editResponseBtn;
    @ViewComponent private JmixButton openSqlBtn;

    private final List<KeyValue> inputItems = new ArrayList<>();
    private final List<KeyValue> headerItems = new ArrayList<>();
    private final List<KeyValue> extractItems = new ArrayList<>();
    private final List<QueryFilter> filterItems = new ArrayList<>();
    private final List<KeyValue> responseItems = new ArrayList<>();
    private String apiBody = "";
    private String sqlInput = "";

    private Grid<KeyValue> inputGrid;
    private Grid<KeyValue> headerGrid;
    private Grid<KeyValue> extractGrid;
    private Grid<QueryFilter> filterGrid;
    private Grid<KeyValue> responseGrid;

    private Dialog inputDialog;
    private Dialog headersDialog;
    private Dialog bodyDialog;
    private Dialog extractDialog;
    private Dialog filtersDialog;
    private Dialog responseDialog;
    private Dialog sqlDialog;

    @Autowired private DynamicExecutionService dynamicExecutionService;
    @Autowired private QueryableEntityRegistry queryableEntityRegistry;
    @Autowired private SqlQueryParser sqlQueryParser;
    @Autowired private CamelDslEmitter camelDslEmitter;

    @Subscribe
    public void onInit(InitEvent event) {
        apiMethodField.setItems(HTTP_METHODS);
        apiMethodField.setValue("GET");

        dbOrderDirField.setItems(ORDER_DIRS);
        dbEntityField.setItems(new ArrayList<>(queryableEntityRegistry.allEntities()));

        inputDialog = buildKvDialog("Input variables", inputItems,
                "Tên biến", "Giá trị", g -> inputGrid = g);
        headersDialog = buildKvDialog("Request headers", headerItems,
                "Header name", "Header value", g -> headerGrid = g);
        extractDialog = buildKvDialog("Extract rules (JSONPath)", extractItems,
                "Field", "JSONPath (vd: $.name)", g -> extractGrid = g);
        responseDialog = buildKvDialog("Response template", responseItems,
                "Key", "Expression (vd: ${extracted.name})", g -> responseGrid = g);
        filtersDialog = buildFilterDialog();
        bodyDialog = buildBodyDialog();
        sqlDialog = buildSqlDialog();

        editInputBtn.addClickListener(e -> inputDialog.open());
        editHeadersBtn.addClickListener(e -> headersDialog.open());
        editBodyBtn.addClickListener(e -> bodyDialog.open());
        editExtractBtn.addClickListener(e -> extractDialog.open());
        editFiltersBtn.addClickListener(e -> filtersDialog.open());
        editResponseBtn.addClickListener(e -> responseDialog.open());
        openSqlBtn.addClickListener(e -> sqlDialog.open());

        loadSample();
    }

    @Subscribe(id = "loadSampleBtn", subject = "clickListener")
    public void onLoadSample(ClickEvent<JmixButton> e) {
        loadSample();
    }

    @Subscribe(id = "resetBtn", subject = "clickListener")
    public void onReset(ClickEvent<JmixButton> e) {
        clearForm();
        sqlInput = "";
        previewArea.setValue("");
        resultArea.setValue("");
    }

    @Subscribe(id = "previewYamlBtn", subject = "clickListener")
    public void onPreviewYaml(ClickEvent<JmixButton> e) {
        try {
            commitGridEditors();
            previewArea.setValue(camelDslEmitter.toYaml(buildConfig()));
        } catch (Exception ex) {
            previewArea.setValue("Error: " + rootMessage(ex));
        }
    }

    @Subscribe(id = "previewXmlBtn", subject = "clickListener")
    public void onPreviewXml(ClickEvent<JmixButton> e) {
        try {
            commitGridEditors();
            previewArea.setValue(camelDslEmitter.toXml(buildConfig()));
        } catch (Exception ex) {
            previewArea.setValue("Error: " + rootMessage(ex));
        }
    }

    @Subscribe(id = "executeBtn", subject = "clickListener")
    public void onExecute(ClickEvent<JmixButton> e) {
        try {
            commitGridEditors();
            ExecutionConfig config = buildConfig();
            Object result = dynamicExecutionService.execute(config);
            resultArea.setValue(yamlMapper.writeValueAsString(result));
        } catch (Exception ex) {
            resultArea.setValue("Error: " + rootMessage(ex));
        }
    }

    private ExecutionConfig buildConfig() {
        ExecutionConfig cfg = new ExecutionConfig();

        cfg.setInput(toMap(inputItems, true));

        ApiConfig api = new ApiConfig();
        api.setMethod(apiMethodField.getValue());
        api.setUrl(apiUrlField.getValue());
        Map<String, Object> headers = toMap(headerItems, false);
        api.setHeaders(headers.isEmpty() ? null : toStringMap(headers));
        api.setBody(apiBody == null || apiBody.isBlank() ? null : apiBody);
        cfg.setApi(api);

        Map<String, Object> extractMap = toMap(extractItems, false);
        cfg.setExtract(extractMap.isEmpty() ? null : toStringMap(extractMap));

        String entity = dbEntityField.getValue();
        if (entity != null && !entity.isBlank()) {
            DbQueryConfig db = new DbQueryConfig();
            db.setEntity(entity);
            db.setOrderBy(blankToNull(dbOrderByField.getValue()));
            db.setOrderDir(dbOrderDirField.getValue());
            db.setLimit(dbLimitField.getValue());
            List<QueryFilter> filters = new ArrayList<>();
            for (QueryFilter f : filterItems) {
                if (f.getField() == null || f.getField().isBlank()) continue;
                QueryFilter copy = new QueryFilter();
                copy.setField(f.getField());
                copy.setOp(f.getOp());
                copy.setValue(f.getValue());
                filters.add(copy);
            }
            db.setFilters(filters.isEmpty() ? null : filters);
            cfg.setDbQuery(db);
        }

        Map<String, Object> response = toMap(responseItems, false);
        cfg.setResponse(response.isEmpty() ? null : response);

        return cfg;
    }

    private void loadSample() {
        clearForm();

        inputItems.add(new KeyValue("userId", "1"));
        inputItems.add(new KeyValue("token", "PASTE_JWT_TOKEN_HERE"));

        apiMethodField.setValue("GET");
        apiUrlField.setValue("http://localhost:8081/api/users/${userId}");
        headerItems.add(new KeyValue("Authorization", "Bearer ${token}"));

        extractItems.add(new KeyValue("name", "$.name"));
        extractItems.add(new KeyValue("email", "$.email"));
        extractItems.add(new KeyValue("phone", "$.phone"));

        dbEntityField.setValue("customer");
        dbOrderDirField.setValue("ASC");
        dbLimitField.setValue(1);
        QueryFilter f = new QueryFilter();
        f.setField("phone");
        f.setOp("=");
        f.setValue("${extracted.phone}");
        filterItems.add(f);

        responseItems.add(new KeyValue("apiUser", "${extracted}"));
        responseItems.add(new KeyValue("customerDb", "${dbResult}"));

        sqlInput = "SELECT * FROM customer WHERE phone = '${extracted.phone}' ORDER BY name ASC LIMIT 1";

        refreshAllGrids();
        refreshSummaries();
    }

    private void clearForm() {
        inputItems.clear();
        headerItems.clear();
        extractItems.clear();
        filterItems.clear();
        responseItems.clear();
        apiMethodField.setValue("GET");
        apiUrlField.clear();
        apiBody = "";
        dbEntityField.clear();
        dbOrderByField.clear();
        dbOrderDirField.clear();
        dbLimitField.clear();
        refreshAllGrids();
        refreshSummaries();
    }

    private void refreshAllGrids() {
        if (inputGrid != null) inputGrid.getDataProvider().refreshAll();
        if (headerGrid != null) headerGrid.getDataProvider().refreshAll();
        if (extractGrid != null) extractGrid.getDataProvider().refreshAll();
        if (filterGrid != null) filterGrid.getDataProvider().refreshAll();
        if (responseGrid != null) responseGrid.getDataProvider().refreshAll();
    }

    private void commitGridEditors() {
        if (inputGrid != null && inputGrid.getEditor().isOpen()) inputGrid.getEditor().closeEditor();
        if (headerGrid != null && headerGrid.getEditor().isOpen()) headerGrid.getEditor().closeEditor();
        if (extractGrid != null && extractGrid.getEditor().isOpen()) extractGrid.getEditor().closeEditor();
        if (filterGrid != null && filterGrid.getEditor().isOpen()) filterGrid.getEditor().closeEditor();
        if (responseGrid != null && responseGrid.getEditor().isOpen()) responseGrid.getEditor().closeEditor();
    }

    private void refreshSummaries() {
        editInputBtn.setText(label("Input variables", inputItems.size()));
        editHeadersBtn.setText(label("Headers", headerItems.size()));
        editExtractBtn.setText(label("Extract", extractItems.size()));
        editFiltersBtn.setText(label("Filters", filterItems.size()));
        editResponseBtn.setText(label("Response template", responseItems.size()));
        boolean hasBody = apiBody != null && !apiBody.isBlank();
        editBodyBtn.setText(hasBody ? "Body (đã cấu hình)" : "Body (trống)");
    }

    private String label(String name, int count) {
        return name + " (" + count + ")";
    }

    private Dialog buildKvDialog(String title, List<KeyValue> items,
                                  String keyHeader, String valueHeader,
                                  Consumer<Grid<KeyValue>> gridSetter) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);
        dialog.setWidth("720px");
        dialog.setMaxWidth("90vw");
        dialog.setMaxHeight("80vh");

        VerticalLayout content = new VerticalLayout();
        content.setSpacing(true);
        content.setPadding(false);
        content.setWidthFull();

        Grid<KeyValue> grid = mountKvGrid(content, items, keyHeader, valueHeader);
        gridSetter.accept(grid);

        dialog.add(content);

        Button closeBtn = new Button("Đóng", e -> dialog.close());
        closeBtn.addThemeName("primary");
        dialog.getFooter().add(closeBtn);

        dialog.addOpenedChangeListener(e -> {
            if (!e.isOpened()) {
                refreshSummaries();
            }
        });

        return dialog;
    }

    private Dialog buildFilterDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("DB query filters");
        dialog.setWidth("720px");
        dialog.setMaxWidth("90vw");
        dialog.setMaxHeight("80vh");

        VerticalLayout content = new VerticalLayout();
        content.setSpacing(true);
        content.setPadding(false);
        content.setWidthFull();

        filterGrid = mountFilterGrid(content);

        dialog.add(content);

        Button closeBtn = new Button("Đóng", e -> dialog.close());
        closeBtn.addThemeName("primary");
        dialog.getFooter().add(closeBtn);

        dialog.addOpenedChangeListener(e -> {
            if (!e.isOpened()) {
                refreshSummaries();
            }
        });

        return dialog;
    }

    private Dialog buildBodyDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Request body (JSON cho POST/PUT/PATCH)");
        dialog.setWidth("720px");
        dialog.setHeight("520px");
        dialog.setMaxWidth("90vw");

        TextArea bodyArea = new TextArea();
        bodyArea.setWidthFull();
        bodyArea.setHeight("100%");
        bodyArea.setPlaceholder("{\n  \"key\": \"value\"\n}");

        dialog.add(bodyArea);

        dialog.addOpenedChangeListener(e -> {
            if (e.isOpened()) {
                bodyArea.setValue(apiBody == null ? "" : apiBody);
            } else {
                refreshSummaries();
            }
        });

        Button cancelBtn = new Button("Huỷ", e -> dialog.close());
        Button saveBtn = new Button("Lưu", e -> {
            apiBody = bodyArea.getValue();
            dialog.close();
        });
        saveBtn.addThemeName("primary");
        dialog.getFooter().add(cancelBtn, saveBtn);

        return dialog;
    }

    private Dialog buildSqlDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("SQL quick parser");
        dialog.setWidth("720px");
        dialog.setHeight("440px");
        dialog.setMaxWidth("90vw");

        TextArea sqlArea = new TextArea();
        sqlArea.setLabel("Paste SELECT để tự fill form");
        sqlArea.setWidthFull();
        sqlArea.setHeight("100%");
        sqlArea.setPlaceholder("SELECT * FROM customer WHERE phone = '${extracted.phone}' ORDER BY name ASC LIMIT 1");

        dialog.add(sqlArea);

        dialog.addOpenedChangeListener(e -> {
            if (e.isOpened()) {
                sqlArea.setValue(sqlInput == null ? "" : sqlInput);
            }
        });

        Button cancelBtn = new Button("Đóng", e -> dialog.close());
        Button parseBtn = new Button("Parse → Form", ev -> {
            String sql = sqlArea.getValue();
            sqlInput = sql;
            if (sql == null || sql.isBlank()) {
                resultArea.setValue("Nhập câu SELECT vào ô SQL trước.");
                return;
            }
            try {
                DbQueryConfig parsed = sqlQueryParser.parse(sql);
                applyParsedQuery(parsed);
                resultArea.setValue("Đã parse SQL → form.");
                dialog.close();
            } catch (Exception ex) {
                resultArea.setValue("Lỗi parse SQL: " + rootMessage(ex));
            }
        });
        parseBtn.addThemeName("primary");
        dialog.getFooter().add(cancelBtn, parseBtn);

        return dialog;
    }

    private void applyParsedQuery(DbQueryConfig parsed) {
        dbEntityField.setValue(parsed.getEntity());
        dbOrderByField.setValue(parsed.getOrderBy() == null ? "" : parsed.getOrderBy());
        dbOrderDirField.setValue(parsed.getOrderDir() == null ? "ASC" : parsed.getOrderDir());
        dbLimitField.setValue(parsed.getLimit());

        filterItems.clear();
        if (parsed.getFilters() != null) {
            filterItems.addAll(parsed.getFilters());
        }
        if (filterGrid != null) filterGrid.getDataProvider().refreshAll();
        refreshSummaries();
    }

    private Grid<KeyValue> mountKvGrid(VerticalLayout container, List<KeyValue> items,
                                       String keyHeader, String valueHeader) {
        Grid<KeyValue> grid = new Grid<>();
        grid.setItems(items);
        grid.setAllRowsVisible(true);
        grid.setWidthFull();

        Editor<KeyValue> editor = grid.getEditor();
        Binder<KeyValue> binder = new Binder<>(KeyValue.class);
        editor.setBinder(binder);
        editor.setBuffered(false);

        TextField keyEditor = new TextField();
        keyEditor.setWidthFull();
        binder.bind(keyEditor, KeyValue::getKey, KeyValue::setKey);

        TextField valueEditor = new TextField();
        valueEditor.setWidthFull();
        binder.bind(valueEditor, KeyValue::getValue, KeyValue::setValue);

        grid.addColumn(KeyValue::getKey).setHeader(keyHeader).setEditorComponent(keyEditor).setFlexGrow(1);
        grid.addColumn(KeyValue::getValue).setHeader(valueHeader).setEditorComponent(valueEditor).setFlexGrow(2);
        grid.addComponentColumn(item -> {
            Button del = new Button(VaadinIcon.TRASH.create(), e -> {
                items.remove(item);
                grid.getDataProvider().refreshAll();
                refreshSummaries();
            });
            del.addThemeName("tertiary-inline");
            return del;
        }).setHeader("").setFlexGrow(0).setWidth("4em");

        grid.addItemClickListener(e -> editor.editItem(e.getItem()));

        Button addBtn = new Button("Thêm dòng", VaadinIcon.PLUS.create(), e -> {
            KeyValue kv = new KeyValue("", "");
            items.add(kv);
            grid.getDataProvider().refreshAll();
            editor.editItem(kv);
            refreshSummaries();
        });
        addBtn.addThemeName("tertiary");

        container.add(grid, addBtn);
        return grid;
    }

    private Grid<QueryFilter> mountFilterGrid(VerticalLayout container) {
        Grid<QueryFilter> grid = new Grid<>();
        grid.setItems(filterItems);
        grid.setAllRowsVisible(true);
        grid.setWidthFull();

        Editor<QueryFilter> editor = grid.getEditor();
        Binder<QueryFilter> binder = new Binder<>(QueryFilter.class);
        editor.setBinder(binder);
        editor.setBuffered(false);

        TextField fieldEditor = new TextField();
        fieldEditor.setWidthFull();
        binder.bind(fieldEditor, QueryFilter::getField, QueryFilter::setField);

        ComboBox<String> opEditor = new ComboBox<>();
        opEditor.setItems(FILTER_OPS);
        opEditor.setWidthFull();
        binder.bind(opEditor, QueryFilter::getOp, QueryFilter::setOp);

        TextField valueEditor = new TextField();
        valueEditor.setWidthFull();
        binder.bind(valueEditor,
                f -> f.getValue() == null ? "" : f.getValue().toString(),
                (f, v) -> f.setValue(v));

        grid.addColumn(QueryFilter::getField).setHeader("Field")
                .setEditorComponent(fieldEditor).setFlexGrow(1);
        grid.addColumn(QueryFilter::getOp).setHeader("Op")
                .setEditorComponent(opEditor).setFlexGrow(0).setWidth("8em");
        grid.addColumn(f -> f.getValue() == null ? "" : f.getValue().toString())
                .setHeader("Value").setEditorComponent(valueEditor).setFlexGrow(2);
        grid.addComponentColumn(item -> {
            Button del = new Button(VaadinIcon.TRASH.create(), e -> {
                filterItems.remove(item);
                grid.getDataProvider().refreshAll();
                refreshSummaries();
            });
            del.addThemeName("tertiary-inline");
            return del;
        }).setHeader("").setFlexGrow(0).setWidth("4em");

        grid.addItemClickListener(e -> editor.editItem(e.getItem()));

        Button addBtn = new Button("Thêm filter", VaadinIcon.PLUS.create(), e -> {
            QueryFilter f = new QueryFilter();
            f.setOp("=");
            filterItems.add(f);
            grid.getDataProvider().refreshAll();
            editor.editItem(f);
            refreshSummaries();
        });
        addBtn.addThemeName("tertiary");

        container.add(grid, addBtn);
        return grid;
    }

    private Map<String, Object> toMap(List<KeyValue> items, boolean keepEmptyValue) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (KeyValue kv : items) {
            String k = kv.getKey();
            if (k == null || k.isBlank()) continue;
            String v = kv.getValue();
            if (!keepEmptyValue && (v == null || v.isEmpty())) continue;
            m.put(k.trim(), v);
        }
        return m;
    }

    private Map<String, String> toStringMap(Map<String, Object> in) {
        Map<String, String> out = new LinkedHashMap<>();
        in.forEach((k, v) -> out.put(k, v == null ? null : v.toString()));
        return out;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private String rootMessage(Exception e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + " - " + root.getMessage();
    }

    public static class KeyValue {
        private String key;
        private String value;

        public KeyValue() {}

        public KeyValue(String key, String value) {
            this.key = key;
            this.value = value;
        }

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}
