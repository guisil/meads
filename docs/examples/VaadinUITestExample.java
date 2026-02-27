// == VaadinUITestExample.java ==
// Server-side Vaadin UI tests using Karibu Testing. No browser needed.
// USE WHEN: Testing view rendering, form validation, button actions, grids.

package com.example.app.order;

import com.example.app.TestcontainersConfiguration;
import com.example.app.order.internal.OrderView;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.spring.MockSpringServlet;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.UI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import static com.github.mvysny.kaributesting.v10.LocatorJ.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OrderViewUITest {

    @Autowired ApplicationContext ctx;

    @BeforeEach
    void setup() {
        MockVaadin.setup(MockSpringServlet.class, ctx);
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
    }

    @Test
    void shouldDisplayOrderViewWithGrid() {
        UI.getCurrent().navigate(OrderView.class);
        assertThat(_get(Grid.class)).isNotNull();
    }

    @Test
    void shouldCreateOrderWhenFormSubmitted() {
        UI.getCurrent().navigate(OrderView.class);

        _get(TextField.class, spec -> spec.withCaption("Product")).setValue("Widget");
        _get(TextField.class, spec -> spec.withCaption("Quantity")).setValue("5");
        _click(_get(Button.class, spec -> spec.withCaption("Place Order")));

        assertThat(_get(Notification.class).getText()).contains("Order created");
    }

    @Test
    void shouldShowValidationErrorWhenFormEmpty() {
        UI.getCurrent().navigate(OrderView.class);
        _click(_get(Button.class, spec -> spec.withCaption("Place Order")));

        var field = _get(TextField.class, spec -> spec.withCaption("Product"));
        assertThat(field.isInvalid()).isTrue();
    }
}

// KEY:
// - MockVaadin.setup(MockSpringServlet.class, ctx) in @BeforeEach
// - MockVaadin.tearDown() in @AfterEach — always
// - _get(Class, spec) finds ONE component. Throws if 0 or 2+.
// - _find(Class, spec) returns all matches.
// - _click() simulates user click.
// - Tests run in 5-60ms — no browser.
// - Needs @SpringBootTest for Vaadin servlet context.
