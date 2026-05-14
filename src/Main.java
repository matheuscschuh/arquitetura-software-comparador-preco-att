import domain.Product;
import service.ProductService;

public class Main {
    public static void main(String[] args) {
        ProductService productService = new ProductService();

        Product produto = new Product("SKU", "asas", 2f);
        produto.setPrice(3f);
        produto.setPrice(4f);
        productService.create(produto);

        productService.listAll();
    }
}
