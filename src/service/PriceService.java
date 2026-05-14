package service;

import adapters.DatabaseStorage;
import domain.Price;

public class PriceService extends BaseService {
    public PriceService() {
        this.armazenamento = new DatabaseStorage<>(Price.class);
    }
}
