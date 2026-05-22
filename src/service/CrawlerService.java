package service;

import adapters.DatabaseStorage;
import adapters.PersistInterface;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import domain.Product;
import domain.ProductLink;
import domain.EntityInterface;

import java.util.ArrayList;
import java.util.List;

public class CrawlerService {
    private final PersistInterface productStorage;

    public CrawlerService() {
        this.productStorage = new DatabaseStorage<>(Product.class);
    }

    private static class ProductSearchResult {
        Float price;
        String url;

        public ProductSearchResult(Float price, String url) {
            this.price = price;
            this.url = url;
        }
    }

    public void crawlAllProducts() {
        List<EntityInterface> entities = productStorage.listAll();
        if (entities.isEmpty()) {
            System.out.println("No products found.");
            return;
        }

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"));
            Page page = context.newPage();
            page.setDefaultNavigationTimeout(20000);
            page.setDefaultTimeout(20000);

            for (EntityInterface entity : entities) {
                Product product = (Product) entity;
                System.out.println("Crawling links for product: " + product.getName());
                Float bestPrice = null;
                String bestStore = null;
                String bestProductUrl = null;

                for (ProductLink link : product.getLinks()) {
                    System.out.println("  Checking search results in " + link.getStoreName() + " at " + link.getUrl());
                    try {
                        page.navigate(link.getUrl(), new Page.NavigateOptions().setTimeout(25000));
                        // Wait a bit for dynamic content
                        page.waitForTimeout(3000);

                        ProductSearchResult result = findBestProductInSearchPage(page, link.getStoreName(), product.getName());
                        if (result != null) {
                            System.out.println("    Found best price: " + result.price + " at: " + result.url);
                            if (bestPrice == null || result.price < bestPrice) {
                                bestPrice = result.price;
                                bestStore = link.getStoreName();
                                bestProductUrl = result.url;
                            }
                        } else {
                            System.out.println("    No matching product found on this page.");
                        }
                    } catch (Exception e) {
                        System.out.println("    Error crawling " + link.getUrl() + ": " + e.getMessage());
                    }
                }

                if (bestPrice != null) {
                    System.out.println("  => Best price for " + product.getName() + " is " + bestPrice + " at " + bestStore + " (Link: " + bestProductUrl + ")");
                    product.setPrice(bestPrice, bestStore, bestProductUrl);
                    productStorage.save(product);
                } else {
                    System.out.println("  => No prices found for " + product.getName());
                }
            }
        }
    }

    private ProductSearchResult findBestProductInSearchPage(Page page, String storeName, String productName) {
        String lowerStore = storeName.toLowerCase();
        List<com.microsoft.playwright.Locator> cards = new ArrayList<>();
        
        String containerSelector = "";
        String titleSelector = "";
        String priceSelector = "";
        String linkSelector = "";
        
        if (lowerStore.contains("mercado livre")) {
            containerSelector = ".ui-search-layout__item, .poly-card";
            titleSelector = ".poly-component__title";
            priceSelector = ".poly-price__current .andes-money-amount__fraction";
            linkSelector = ".poly-component__title";
        } else if (lowerStore.contains("kabum")) {
            containerSelector = ".productCard, a[href*='/produto/']";
            titleSelector = ".nameCard, span[class*='nameCard']";
            priceSelector = ".priceCard, span[class*='priceCard']";
            linkSelector = "a[href*='/produto/']";
        } else if (lowerStore.contains("magazine luiza") || lowerStore.contains("magalu")) {
            containerSelector = "a[data-testid='product-card'], a[href*='/p/']";
            titleSelector = "[data-testid='product-title'], h2";
            priceSelector = "[data-testid='price-value']";
            linkSelector = ""; // container itself is the link
        } else {
            return null;
        }

        try {
            cards = page.locator(containerSelector).all();
            System.out.println("      DEBUG: " + storeName + " title='" + page.title() + "' contentLen=" + page.content().length() + " cardsFound=" + cards.size());
        } catch (Exception e) {
            System.out.println("      Error finding product cards: " + e.getMessage());
            return null;
        }

        Float bestPrice = null;
        String bestUrl = null;

        int cardIndex = 0;
        for (com.microsoft.playwright.Locator card : cards) {
            cardIndex++;
            try {
                String cardText = card.textContent().trim().replace("\n", " ");
                if (cardIndex <= 3) {
                    System.out.println("      DEBUG CARD " + cardIndex + ": " + cardText.substring(0, Math.min(150, cardText.length())));
                }
                String title = "";
                if (!titleSelector.isEmpty()) {
                    com.microsoft.playwright.Locator titleLoc = card.locator(titleSelector).first();
                    if (titleLoc.count() > 0) {
                        title = titleLoc.textContent();
                    }
                }
                
                if (title == null || title.trim().isEmpty()) {
                    if (cardText.contains("R$")) {
                        title = cardText.split("R\\$")[0].trim();
                    } else {
                        title = cardText;
                    }
                }

                if (title == null || title.trim().isEmpty() || !matchesProduct(title, productName)) {
                    continue;
                }

                Float price = null;
                com.microsoft.playwright.Locator priceLoc = card.locator(priceSelector).first();
                if (priceLoc.count() > 0) {
                    String pt = priceLoc.textContent();
                    // Double check priceLoc text doesn't contain installment marker
                    if (!pt.toLowerCase().contains("x de") && !pt.toLowerCase().contains("x de ")) {
                        price = parseBrazilianPrice(pt);
                    }
                }
                
                if (price == null || price <= 0) {
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("R\\$\\s*([0-9.+,]+)");
                    java.util.regex.Matcher matcher = pattern.matcher(cardText);
                    while (matcher.find()) {
                        int start = matcher.start();
                        String preceding = cardText.substring(Math.max(0, start - 12), start).toLowerCase();
                        if (preceding.contains("x de") || preceding.contains("x  de") || preceding.contains("x de ")) {
                            continue; // Skip monthly installments!
                        }
                        Float parsed = parseBrazilianPrice(matcher.group(1));
                        if (parsed != null && parsed > 0) {
                            if (price == null || parsed < price) {
                                price = parsed;
                            }
                        }
                    }
                }

                if (price == null || price <= 0) {
                    continue;
                }

                // Minimum price thresholds to filter out cheap accessories/covers/scams
                String lowerProd = productName.toLowerCase();
                if (lowerProd.contains("playstation 5") || lowerProd.contains("ps5")) {
                    if (price < 2500.0f) {
                        continue;
                    }
                } else if (lowerProd.contains("iphone 16")) {
                    if (price < 3500.0f) {
                        continue;
                    }
                }

                // Get URL
                String url = "";
                if (!linkSelector.isEmpty() && !linkSelector.equals(containerSelector)) {
                    try {
                        com.microsoft.playwright.Locator linkLoc = card.locator(linkSelector).first();
                        if (linkLoc.count() > 0) {
                            url = linkLoc.getAttribute("href");
                        }
                    } catch (Exception ignored) {}
                }
                
                if (url == null || url.isEmpty()) {
                    if (card.count() > 0) {
                        url = card.getAttribute("href");
                    }
                }
                
                if (url == null || url.isEmpty()) {
                    try {
                        com.microsoft.playwright.Locator aLoc = card.locator("a").first();
                        if (aLoc.count() > 0) {
                            url = aLoc.getAttribute("href");
                        }
                    } catch (Exception ignored) {}
                }

                if (url != null && !url.isEmpty()) {
                    if (url.startsWith("/")) {
                        if (lowerStore.contains("kabum")) {
                            url = "https://www.kabum.com.br" + url;
                        } else if (lowerStore.contains("magazine luiza") || lowerStore.contains("magalu")) {
                            url = "https://www.magazineluiza.com.br" + url;
                        } else if (lowerStore.contains("mercado livre")) {
                            url = "https://www.mercadolivre.com.br" + url;
                        }
                    }
                }

                if (bestPrice == null || price < bestPrice) {
                    bestPrice = price;
                    bestUrl = url;
                }
            } catch (Exception e) {
                // Ignore single card extraction error to continue with other cards
            }
        }

        if (bestPrice != null) {
            return new ProductSearchResult(bestPrice, bestUrl);
        }
        return null;
    }

    private boolean matchesProduct(String title, String productName) {
        String lowerTitle = title.toLowerCase();
        String lowerProductName = productName.toLowerCase();
        
        String[] keywords = lowerProductName.split("\\s+");
        for (String keyword : keywords) {
            if (!lowerTitle.contains(keyword)) {
                return false;
            }
        }
        
        // Strict exclusions that apply to everything (including bundles)
        String[] strictExclusions = {
            "capa", "capinha", "pelicula", "película", "cabo", "cabos", "carregador", "fone", "case", "cases", 
            "suporte", "adaptador", "lente", "protetor", "adesivo", "skin", "skins", "bolsa", "maleta", 
            "organizador", "tampa", "tampas", "placa", "chaveiro", "carregamento", "dock", "volante", "bateria"
        };
        for (String acc : strictExclusions) {
            if (lowerTitle.contains(acc)) {
                return false;
            }
        }
        
        // Strong positive indicators of the actual main product to skip general exclusions
        boolean isMainProduct = false;
        if (lowerProductName.contains("playstation") || lowerProductName.contains("ps5")) {
            if (lowerTitle.contains("console") || lowerTitle.contains("videogame") || lowerTitle.contains("video game")) {
                isMainProduct = true;
            }
        } else if (lowerProductName.contains("iphone")) {
            if (lowerTitle.contains("iphone") || lowerTitle.contains("celular") || lowerTitle.contains("smartphone") || lowerTitle.contains("apple")) {
                isMainProduct = true;
            }
        }
        
        if (!isMainProduct) {
            // General accessory terms to exclude when it's NOT explicitly marked as a main product
            String[] generalAccessories = {
                "controle", "headset", "jogo", "game", "caneca", "camiseta"
            };
            for (String acc : generalAccessories) {
                if (lowerTitle.contains(acc)) {
                    return false;
                }
            }
        }
        
        return true;
    }

    private Float parseBrazilianPrice(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        text = text.replaceAll("[^\\d.,]", "");
        if (text.contains(",")) {
            text = text.replace(".", "").replace(",", ".");
        }
        return Float.parseFloat(text);
    }
}
