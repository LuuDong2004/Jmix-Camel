package com.vn.jmixcamel.view.api;


import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.router.Route;
import com.vn.jmixcamel.service.DynamicCamelRouteService;
import com.vn.jmixcamel.view.main.MainView;
import io.jmix.flowui.component.combobox.JmixComboBox;
import io.jmix.flowui.component.textarea.JmixTextArea;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Route(value = "dynamic-route-view", layout = MainView.class)
@ViewController(id = "DynamicRouteView")
@ViewDescriptor(path = "dynamic-route-view.xml")
public class DynamicRouteView extends StandardView {
    @ViewComponent
    private JmixComboBox<Object> dslTypeField;
    @ViewComponent
    private JmixTextArea routeContentArea;
    @ViewComponent
    private JmixButton deployBtn;
    @ViewComponent
    private TypedTextField<Object> executeBodyField;
    @ViewComponent
    private JmixButton executeBtn;
    @ViewComponent
    private JmixTextArea resultArea;
    @ViewComponent
    private TypedTextField<Object> routeIdField;
    @Autowired
    private DynamicCamelRouteService dynamicCamelRouteService;

    @Subscribe
    public void onInit(InitEvent event) {
        dslTypeField.setItems(List.of("XML", "YAML"));
        dslTypeField.setValue("XML");

        routeIdField.setValue("route-user-test");
        executeBodyField.setValue("1");

        routeContentArea.setValue("""
                <camel xmlns="http://camel.apache.org/schema/spring">
                    <route id="route-user-test">
                        <from uri="direct:route-user-test"/>
                        <setHeader name="CamelHttpMethod">
                            <constant>GET</constant>
                        </setHeader>
                        <setHeader name="CamelHttpUri">
                            <simple>https://jsonplaceholder.typicode.com/users/${body}</simple>
                        </setHeader>
                        <to uri="http://dummy"/>
                    </route>
                </camel>
                """);
    }

    @Subscribe(id = "deployBtn", subject = "clickListener")
    public void onDeployBtnClick(final ClickEvent<JmixButton> event) {
        try {
            String routeId = routeIdField.getValue();
            String dslType = dslTypeField.getValue().toString();
            String content = routeContentArea.getValue();

            String result = dynamicCamelRouteService.deployRoute(routeId, dslType, content);
            resultArea.setValue(result);
        } catch (Exception e) {
            resultArea.setValue("Deploy error: " + getRootMessage(e));
        }
    }

    @Subscribe(id = "executeBtn", subject = "clickListener")
    public void onExecuteBtnClick(final ClickEvent<JmixButton> event) {
        try {
            String routeId = routeIdField.getValue();
            String body = executeBodyField.getValue();

            String result = dynamicCamelRouteService.executeRoute(routeId, body);
            resultArea.setValue(result);
        } catch (Exception e) {
            resultArea.setValue("Execute error: " + getRootMessage(e));
        }
    }
    private String getRootMessage(Exception e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + " - " + root.getMessage();
    }
}