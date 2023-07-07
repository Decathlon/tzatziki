package com.decathlon.tzatziki.front.playwright.integration;

import com.decathlon.tzatziki.front.integration.Browser;
import com.decathlon.tzatziki.front.interactions.models.HTMLElement;
import com.decathlon.tzatziki.front.playwright.interactions.models.PlaywrightHTMLElement;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.util.List;

public class PlaywrightBrowser implements Browser {
    private com.microsoft.playwright.Browser playwright;
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
    public List<HTMLElement> find(String cssSelector) {
        if(currentPage == null){
            throw new IllegalStateException("You must navigate to a page before trying to find elements");
        }

        return currentPage.locator(cssSelector).all().stream().map(PlaywrightHTMLElement::new).map(HTMLElement.class::cast).toList();
    }
}
