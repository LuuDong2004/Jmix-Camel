package com.vn.jmixcamel.view.hello;


import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.router.Route;
import com.vn.jmixcamel.service.CamelService;
import com.vn.jmixcamel.view.main.MainView;
import io.jmix.flowui.component.textarea.JmixTextArea;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "hello-view", layout = MainView.class)
@ViewController(id = "HelloView")
@ViewDescriptor(path = "hello-view.xml")
public class HelloView extends StandardView {

    @ViewComponent
    private JmixTextArea resultArea;
    @ViewComponent
    private TypedTextField<Object> nameField;

    @Autowired
    private CamelService camelTrainingService;

    @Subscribe(id = "callCamelBtn", subject = "clickListener")
    public void onCallCamelBtnClick(final ClickEvent<JmixButton> event) {
        String name = nameField.getValue();

        if (name == null || name.isBlank()) {
            resultArea.setValue("Please enter a name");
            return;
        }

        String result = camelTrainingService.sayHello(name);
        resultArea.setValue(result);
    }

    
}