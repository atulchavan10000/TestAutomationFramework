package products;

import framework.client.ApiResponse;
import framework.client.RequestOptions;
import framework.context.TestContext;
import framework.http.HttpResponse;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import products.model.ProductRequest;
import products.model.ProductResponse;
import support.BaseApiTest;
import support.RawApi;
import support.ScenarioData;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/** Seventeen product-service scenarios for filtering, validation and lifecycle. */
public final class ProductServiceTest extends BaseApiTest {
    @DataProvider(name = "validLists")
    public Object[][] validLists() {
        return new Object[][]{
                {"1", "10", null}, {"2", "1", null},
                {"1", "100", "true"}, {"1", "10", "false"}
        };
    }

    @Test(groups = "service", dataProvider = "validLists")
    public void listsProducts(String page, String size, String inStock) {
        RequestOptions.Builder options = RequestOptions.builder()
                .queryParam("page", page).queryParam("pageSize", size);
        if (inStock != null) options.queryParam("inStock", inStock);
        HttpResponse response = raw().get("/api/v1/products", options.build());
        assertThat(response.statusCode(), is(200));
        assertThat(RawApi.body(response), containsString("\"pagination\""));
    }

    @DataProvider(name = "invalidLists")
    public Object[][] invalidLists() {
        return new Object[][]{{"0", "10", null}, {"1", "101", null}, {"1", "10", "maybe"}};
    }

    @Test(groups = "service", dataProvider = "invalidLists")
    public void rejectsInvalidProductFilters(String page, String size, String inStock) {
        RequestOptions.Builder options = RequestOptions.builder()
                .queryParam("page", page).queryParam("pageSize", size);
        if (inStock != null) options.queryParam("inStock", inStock);
        assertThat(raw().get("/api/v1/products", options.build()).statusCode(), is(422));
    }

    @DataProvider(name = "missingProducts")
    public Object[][] missingProducts() { return new Object[][]{{0L}, {999_999_999L}}; }

    @Test(groups = "service", dataProvider = "missingProducts")
    public void returnsNotFoundForUnknownProduct(long id) {
        assertThat(raw().get("/api/v1/products/" + id).statusCode(), is(404));
    }

    @DataProvider(name = "invalidProducts")
    public Object[][] invalidProducts() {
        return new Object[][]{
                {"{\"name\":\"\",\"category\":\"TEST\",\"price\":1,\"stock\":1,\"rating\":1}"},
                {"{\"name\":\"Zero\",\"category\":\"TEST\",\"price\":0,\"stock\":1,\"rating\":1}"},
                {"{\"name\":\"Negative\",\"category\":\"TEST\",\"price\":-1,\"stock\":1,\"rating\":1}"},
                {"{\"name\":\"Stock\",\"category\":\"TEST\",\"price\":1,\"stock\":-1,\"rating\":1}"},
                {"{\"name\":\"Rating\",\"category\":\"TEST\",\"price\":1,\"stock\":1,\"rating\":6}"}
        };
    }

    @Test(groups = "service", dataProvider = "invalidProducts")
    public void rejectsInvalidProduct(String json) {
        assertThat(raw().postJson("/api/v1/products", json).statusCode(), is(422));
    }

    @DataProvider(name = "validProducts")
    public Object[][] validProducts() {
        return new Object[][]{
                {"BOOKS", "1.00", 0, 0.0}, {"ELECTRONICS", "999.99", 1, 5.0},
                {"TEST", "125.50", 25, 4.2}
        };
    }

    @Test(groups = {"service", "destructive"}, dataProvider = "validProducts")
    public void createsReadsAndDeletesProduct(String category, String price, int stock, double rating) {
        ProductApi products = api();
        ProductRequest request = new ProductRequest(ScenarioData.unique("Jenkins product"), category,
                new BigDecimal(price), "INR", stock, rating, "Owned by this test");
        ApiResponse<ProductResponse> created = products.create(request);
        long id = created.body().id();
        try {
            assertThat(created.rawResponse().statusCode(), is(201));
            assertThat(created.body().price(), comparesEqualTo(new BigDecimal(price)));
            assertThat(products.get(id).body().stock(), is(stock));
        } finally {
            assertThat(products.delete(id).statusCode(), is(204));
        }
    }

    private ProductApi api() {
        return new ProductApi(client("product-service", new TestContext(UUID.randomUUID().toString())));
    }

    private RawApi raw() {
        return new RawApi(client("product-service", new TestContext(UUID.randomUUID().toString())));
    }
}
