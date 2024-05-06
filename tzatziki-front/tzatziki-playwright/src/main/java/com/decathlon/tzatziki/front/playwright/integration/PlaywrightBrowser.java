package com.decathlon.tzatziki.front.playwright.integration;

import com.decathlon.tzatziki.front.integration.Browser;
import com.decathlon.tzatziki.front.interactions.models.HTMLElement;
import com.decathlon.tzatziki.front.playwright.interactions.models.PlaywrightHTMLElement;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.List;

public class PlaywrightBrowser implements Browser {
    private final com.microsoft.playwright.Browser playwright;
    private Page currentPage;

    public PlaywrightBrowser() {
        Playwright base = Playwright.create();
        BrowserType browserType = switch(com.decathlon.tzatziki.front.integration.BrowserType.get()){
            case CHROME -> base.chromium();
            case FIREFOX -> base.firefox();
            case EDGE -> base.webkit();
            case IE -> base.webkit();
            case SAFARI -> base.webkit();
        };

        this.playwright = browserType.launch(new BrowserType.LaunchOptions());
    }

    @Override
    public void get(String page) {
        currentPage = playwright.newPage();
        currentPage.navigate(page);
    }

    @Override
    public List<HTMLElement> find(String selector) {
        if(currentPage == null){
            throw new IllegalStateException("You must navigate to a page before trying to find elements");
        }

        return currentPage.locator(selector).all().stream().map(PlaywrightHTMLElement::new).map(HTMLElement.class::cast).toList();
    }

    @Override
    public boolean waitForElement(String selector, boolean isVisible, Integer timeoutMs) {
        try {
            currentPage.locator(selector).waitFor(new Locator.WaitForOptions()
                    .setState(Boolean.TRUE.equals(isVisible) ? WaitForSelectorState.VISIBLE : WaitForSelectorState.ATTACHED)
                    .setTimeout(timeoutMs));
        } catch (TimeoutError e) {
            return false;
        }
        return true;
    }

    @Override
    public boolean waitForPage(String page, Integer timeoutMs) {
        try {
            currentPage.waitForURL(page, new Page.WaitForURLOptions().setTimeout(timeoutMs));
        } catch (TimeoutError e) {
            return false;
        }
        return true;
    }

    @Override
    public void reload() {
        currentPage.reload();
    }

    @Override
    public void close() {
        currentPage.close();
    }
}
