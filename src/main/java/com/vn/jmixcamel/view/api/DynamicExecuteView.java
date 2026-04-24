package com.vn.jmixcamel.view.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.router.Route;
import com.vn.jmixcamel.dto.ExecutionConfig;
import com.vn.jmixcamel.service.DynamicExecutionService;
import com.vn.jmixcamel.view.main.MainView;
import io.jmix.flowui.component.textarea.JmixTextArea;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "dynamic-execute-view", layout = MainView.class)
@ViewController(id = "DynamicExecuteView")
@ViewDescriptor(path = "dynamic-execute-view.xml")
public class DynamicExecuteView extends StandardView {

    private static final String SAMPLE_CONFIG = """
            {
              "input": {
                "id": "1"
              },
              "api": {
                "method": "GET",
                "url": "https://jsonplaceholder.typicode.com/users/${id}"
              },
              "extract": {
                "name": "$.name",
                "phone": "$.phone"
              },
              "dbLookup": {
                "type": "customer",
                "by": ["name", "phone"]
              }
            }
            """;

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @ViewComponent
    private JmixTextArea configArea;

    @ViewComponent
    private JmixTextArea resultArea;

    @ViewComponent
    private JmixButton executeBtn;

    @ViewComponent
    private JmixButton sampleBtn;

    @Autowired
    private DynamicExecutionService dynamicExecutionService;

    @Subscribe
    public void onInit(InitEvent event) {
        configArea.setValue(SAMPLE_CONFIG);
    }

    @Subscribe(id = "sampleBtn", subject = "clickListener")
    public void onSampleBtnClick(ClickEvent<JmixButton> event) {
        configArea.setValue(SAMPLE_CONFIG);
        resultArea.setValue("");
    }

    @Subscribe(id = "executeBtn", subject = "clickListener")
    public void onExecuteBtnClick(ClickEvent<JmixButton> event) {
        String raw = configArea.getValue();
        if (raw == null || raw.isBlank()) {
            resultArea.setValue("Config is empty");
            return;
        }
        try {
            ExecutionConfig config = mapper.readValue(raw, ExecutionConfig.class);
            Object result = dynamicExecutionService.execute(config);
            resultArea.setValue(mapper.writeValueAsString(result));
        } catch (Exception e) {
            resultArea.setValue("Error: " + rootMessage(e));
        }
    }

    private String rootMessage(Exception e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + " - " + root.getMessage();
    }
}
