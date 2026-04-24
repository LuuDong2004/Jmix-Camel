package com.vn.jmixcamel.view.api;


import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.router.Route;
import com.vn.jmixcamel.service.CamelService;
import com.vn.jmixcamel.view.main.MainView;
import io.jmix.flowui.component.combobox.JmixComboBox;
import io.jmix.flowui.component.textarea.JmixTextArea;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Route(value = "api-view", layout = MainView.class)
@ViewController(id = "ApiView")
@ViewDescriptor(path = "api-view.xml")
public class ApiView extends StandardView {
    @Autowired
    private CamelService camelTrainingService;

    @ViewComponent
    private JmixComboBox<Object> methodField;

    @ViewComponent
    private TypedTextField<Object> userIdField;
    @ViewComponent
    private TypedTextField<Object> urlField;
    @ViewComponent
    private JmixTextArea resultArea;

    @Subscribe
    public void onInit(InitEvent event) {
        methodField.setItems(List.of("GET", "POST", "PUT", "DELETE"));
        methodField.setValue("GET");

        urlField.setValue("https://jsonplaceholder.typicode.com/users/${userId}");
    }

    @Subscribe(id = "callApiBtn", subject = "clickListener")
    public void onCallApiBtnClick(final ClickEvent<JmixButton> event) {
        String method = methodField.getValue().toString();
        String url = urlField.getValue();
        String userId = userIdField.getValue();

        if (method == null || method.isBlank()) {
            resultArea.setValue("Please select HTTP method");
            return;
        }

        if (url == null || url.isBlank()) {
            resultArea.setValue("Please enter URL template");
            return;
        }

        if (userId == null || userId.isBlank()) {
            resultArea.setValue("Please enter user id");
            return;
        }

        try {
            Map<String, Object> request = new HashMap<>();
            request.put("method", method);
            request.put("url", url);
            request.put("userId", userId);

            String result = camelTrainingService.executeApi(request);
            resultArea.setValue(result);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            resultArea.setValue("Error: " + root.getClass().getSimpleName() + " - " + root.getMessage());
        }
    }

}