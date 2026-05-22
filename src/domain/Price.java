package domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "price")
public class Price implements EntityInterface {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "uuid", length = 36)
    private UUID uuid;

    @Column(name = "price")
    private Float price;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "date")
    private Date date;

    @Column(name = "store_name")
    private String storeName;

    @Column(name = "product_url", length = 1000)
    private String productUrl;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Price() {
    }

    public Float getPrice() {
        return price;
    }

    public void setPrice(Float price) {
        this.price = price;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public Price(Float price, Date date) {
        this.price = price;
        this.date = date;
    }

    public Price(Float price, Date date, String storeName) {
        this.price = price;
        this.date = date;
        this.storeName = storeName;
    }

    public Price(Float price, Date date, String storeName, String productUrl) {
        this.price = price;
        this.date = date;
        this.storeName = storeName;
        this.productUrl = productUrl;
    }

    public Price(UUID uuid, Float price, Date date) {
        this.uuid = uuid;
        this.price = price;
        this.date = date;
    }

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public String getProductUrl() {
        return productUrl;
    }

    public void setProductUrl(String productUrl) {
        this.productUrl = productUrl;
    }

    @Override
    public UUID getUUID() {
        return this.uuid;
    }

    @Override
    public String toString() {
        return "Price{" +
                "price=" + price +
                ", date=" + date +
                ", storeName='" + storeName + '\'' +
                ", productUrl='" + productUrl + '\'' +
                '}';
    }
}
