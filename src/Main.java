import domain.Product;
import domain.ProductLink;
import service.CrawlerService;
import service.ProductService;

public class Main {
    public static void main(String[] args) {
        ProductService productService = new ProductService();

        // Clean slate for testing
        System.out.println("Cleaning database for fresh execution...");
        productService.getAllProducts().forEach(productService::delete);

        // Seed PlayStation 5
        System.out.println("Seeding PlayStation 5...");
        Product ps5 = new Product("PS5-2026", "PlayStation 5", null);
        
        ProductLink kabumPs5 = new ProductLink("Kabum", "https://www.kabum.com.br/busca/playstation-5");
        kabumPs5.setProduct(ps5);
        ps5.getLinks().add(kabumPs5);

        ProductLink mlPs5 = new ProductLink("Mercado Livre", "https://lista.mercadolivre.com.br/playstation-5");
        mlPs5.setProduct(ps5);
        ps5.getLinks().add(mlPs5);

        ProductLink magaluPs5 = new ProductLink("Magazine Luiza", "https://www.magazineluiza.com.br/busca/playstation+5/");
        magaluPs5.setProduct(ps5);
        ps5.getLinks().add(magaluPs5);

        productService.create(ps5);

        // Seed iPhone 16
        System.out.println("Seeding iPhone 16...");
        Product iphone16 = new Product("IPHONE16-2026", "iPhone 16", null);

        ProductLink kabumIphone = new ProductLink("Kabum", "https://www.kabum.com.br/busca/iphone-16");
        kabumIphone.setProduct(iphone16);
        iphone16.getLinks().add(kabumIphone);

        ProductLink mlIphone = new ProductLink("Mercado Livre", "https://lista.mercadolivre.com.br/iphone-16");
        mlIphone.setProduct(iphone16);
        iphone16.getLinks().add(mlIphone);

        ProductLink magaluIphone = new ProductLink("Magazine Luiza", "https://www.magazineluiza.com.br/busca/iphone+16/");
        magaluIphone.setProduct(iphone16);
        iphone16.getLinks().add(magaluIphone);

        productService.create(iphone16);

        // Run the crawler
        System.out.println("\nStarting the price crawler...");
        CrawlerService crawler = new CrawlerService();
        crawler.crawlAllProducts();

        // Print results
        System.out.println("\n=== RESULTS ===");
        productService.getAllProducts().forEach(p -> {
            System.out.println("\nProduct Name: " + p.getName());
            System.out.println("SKU: " + p.getSku());
            System.out.println("Current Best Price: R$ " + p.getPrice() + " at " + p.getStoreName());
            System.out.println("Cheapest Product Link: " + p.getBestProductUrl());
            System.out.println("Price History Logs:");
            if (p.getHistoricalPrice().isEmpty()) {
                System.out.println("  No historical price entries yet.");
            } else {
                p.getHistoricalPrice().forEach(hp -> {
                    System.out.println("  - R$ " + hp.getPrice() + " on " + hp.getDate() + " at " + hp.getStoreName() + " (URL: " + hp.getProductUrl() + ")");
                });
            }
        });
    }
}
