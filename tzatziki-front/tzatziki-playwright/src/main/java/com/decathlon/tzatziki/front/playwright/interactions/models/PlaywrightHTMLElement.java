package com.decathlon.tzatziki.front.playwright.interactions.models;

import com.decathlon.tzatziki.front.interactions.Action;
import com.decathlon.tzatziki.front.interactions.models.HTMLElement;
import com.microsoft.playwright.Locator;
import lombok.AllArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
@SuppressWarnings("unchecked")
public class PlaywrightHTMLElement implements HTMLElement {
    private Locator locator;

    @Override
    public String getId() {
        return locator.getAttribute("id");
    }

    @Override
    public List<String> getClasses() {
        return Arrays.stream(locator.getAttribute("class").split(" ")).toList();
    }

    @Override
    public Map<String, Object> getAttributes() {
        Map<String, Object> attributes = ((List<String>) locator.evaluate("element => element.getAttributeNames()")).stream().collect(Collectors.toMap(
                attribute -> attribute,
                attribute -> locator.getAttribute(attribute)
        ));
        try {
            attributes.put("inputValue", locator.inputValue());
        } catch (Exception e) {
            // Not an input value
        }
        return attributes;
    }

    @Override
    public String getStyleValue(String styleProperty) {
        return (String) locator.evaluate("element => window.getComputedStyle(element).getPropertyValue('" + styleProperty + "')");
    }

    @Override
    public HTMLElement performAction(Action action, String... params) {
        switch(action){
            case CLICK -> locator.click();
            case FILL -> locator.fill(params[0]);
        }

        return this;
    }
}
