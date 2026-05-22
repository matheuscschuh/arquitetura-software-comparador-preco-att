package service;

import adapters.DatabaseStorage;
import domain.Product;

public class ProductService extends BaseService {
    public ProductService() {
        this.armazenamento = new DatabaseStorage<>(Product.class);
    }

    public java.util.List<Product> getAllProducts() {
        java.util.List<domain.EntityInterface> entities = this.armazenamento.listAll();
        java.util.List<Product> products = new java.util.ArrayList<>();
        for (domain.EntityInterface e : entities) {
            products.add((Product) e);
        }
        return products;
    }
}
