package in.medmate.pharmacy_server.stock.web;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.medmate.pharmacy_server.distributors.store.DistributorStore;
import in.medmate.pharmacy_server.identity.access.AccountAuthentication;
import in.medmate.pharmacy_server.identity.access.AccountPrincipal;
import in.medmate.pharmacy_server.identity.access.AccountType;
import in.medmate.pharmacy_server.identity.access.PharmacyRole;
import in.medmate.pharmacy_server.identity.access.VerificationStatus;
import in.medmate.pharmacy_server.shared.error.ApiExceptionHandler;
import in.medmate.pharmacy_server.stock.MedicineCatalog;
import in.medmate.pharmacy_server.stock.store.StockStore;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StockControllerTest {

    private static final UUID PHARMACY = UUID.fromString("00000000-0000-4000-8000-000000000010");

    private final StockStore stock = Mockito.mock(StockStore.class);
    private final DistributorStore distributors = Mockito.mock(DistributorStore.class);
    private final MedicineCatalog catalog = Mockito.mock(MedicineCatalog.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new StockController(new StockService(stock, distributors, catalog)))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void readIsRequiredToListPurchases() throws Exception {
        mvc.perform(get("/pharmacy/purchases").principal(staff("purchases:create")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Purchases cannot be read"));
        verify(stock, never()).purchases(any());
    }

    @Test
    void createIsRequiredToSaveABill() throws Exception {
        mvc.perform(post("/pharmacy/purchases")
                        .principal(staff("purchases:read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"distributorId\":\"DST-001\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Purchases cannot be created"));
        verify(stock, never()).insertPurchase(any(), any(), any(), any(), any(), any(), any(int.class));
    }

    @Test
    void returnIsRequiredToSendStockBack() throws Exception {
        mvc.perform(post("/pharmacy/purchases/PUR-001/returns")
                        .principal(staff("purchases:read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Purchases cannot be returned"));
        verify(stock, never()).insertReturn(any(), any(), any(), any(), any(int.class));
    }

    @Test
    void aBadPurchasePageIsRejected() throws Exception {
        mvc.perform(get("/pharmacy/purchases").param("page", "0").principal(staff("purchases:read")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Page is not valid"));
    }

    @Test
    void purchaseStatsStayWithThePage() throws Exception {
        when(stock.documentPage(
                        any(),
                        any(),
                        anyString(),
                        anyString(),
                        nullable(LocalDate.class),
                        nullable(LocalDate.class),
                        anyInt(),
                        anyInt()))
                .thenReturn(new StockStore.DocumentPage(List.of(), 4));
        when(stock.monthTotals(any(), any(), any())).thenReturn(new StockStore.MonthTotals(12, 1, 0, 0));
        when(distributors.outstanding(any())).thenReturn(9L);
        mvc.perform(get("/pharmacy/purchases").principal(staff("purchases:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.stats.purchasesThisMonth").value(12))
                .andExpect(jsonPath("$.stats.outstanding").value(9));
    }

    @Test
    void aPurchaseInvoiceSearchReachesTheStore() throws Exception {
        when(stock.documentPage(
                        any(),
                        any(),
                        anyString(),
                        anyString(),
                        nullable(LocalDate.class),
                        nullable(LocalDate.class),
                        anyInt(),
                        anyInt()))
                .thenReturn(new StockStore.DocumentPage(List.of(), 1));
        when(stock.monthTotals(any(), any(), any())).thenReturn(new StockStore.MonthTotals(0, 0, 0, 0));
        when(distributors.outstanding(any())).thenReturn(0L);
        mvc.perform(get("/pharmacy/purchases")
                        .param("q", "H00-00")
                        .param("from", "2025-05-13")
                        .principal(staff("purchases:read")))
                .andExpect(status().isOk());
        verify(stock)
                .documentPage(
                        eq(PHARMACY),
                        isNull(),
                        eq("%h00-00%"),
                        eq(""),
                        eq(LocalDate.parse("2025-05-13")),
                        isNull(),
                        eq(25),
                        eq(0));
    }

    @Test
    void billingCanReadAPageOfInventory() throws Exception {
        when(stock.inventoryPage(
                        any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(new StockStore.InventoryPage(List.of(), 2));
        when(stock.inventoryTotals(any(), anyString(), anyString()))
                .thenReturn(new StockStore.InventoryTotals(
                        2, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 2, 0, BigDecimal.ZERO));
        when(stock.productsIn(any(), any())).thenReturn(List.of());
        when(stock.batchesIn(any(), any())).thenReturn(List.of());
        mvc.perform(get("/pharmacy/inventory").param("filter", "out").principal(staff("billing:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.summary.outCount").value(2));
    }

    @Test
    void anUnknownInventoryFilterIsRejected() throws Exception {
        mvc.perform(get("/pharmacy/inventory").param("filter", "nope").principal(staff("inventory:read")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Filter is not valid"));
    }

    @Test
    void readIsRequiredToListInventory() throws Exception {
        mvc.perform(get("/pharmacy/inventory").principal(staff("inventory:update")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Inventory cannot be read"));
        verify(stock, never()).products(any());
    }

    @Test
    void updateIsRequiredToChangeReorder() throws Exception {
        mvc.perform(patch("/pharmacy/inventory/DR100")
                        .principal(staff("inventory:read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reorderLevel\":8}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Inventory cannot be updated"));
        verify(stock, never()).updateReorder(any(), any(), any(int.class));
    }

    @Test
    void createIsRequiredToSearchAndValidate() throws Exception {
        mvc.perform(get("/pharmacy/medicines/search")
                        .principal(staff("purchases:read"))
                        .param("q", "crocin"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Purchases cannot be created"));
        mvc.perform(post("/pharmacy/medicines/validate")
                        .principal(staff("purchases:read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"DR100\"]}"))
                .andExpect(status().isForbidden());
        verify(catalog, never()).search(any(), any(int.class));
        verify(catalog, never()).lookup(any());
    }

    @Test
    void readIsRequiredToListRacks() throws Exception {
        mvc.perform(get("/pharmacy/racks").principal(staff("racks:create")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Racks cannot be read"));
        verify(stock, never()).rackCodes(any());
        verify(stock, never()).products(any());
    }

    @Test
    void createIsRequiredToAddARack() throws Exception {
        mvc.perform(post("/pharmacy/racks")
                        .principal(staff("racks:read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rack\":\"A1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Racks cannot be created"));
        verify(stock, never()).insertRack(any(), any());
    }

    @Test
    void updateIsRequiredToPlaceAProduct() throws Exception {
        mvc.perform(patch("/pharmacy/inventory/DR100/location")
                        .principal(staff("racks:read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rack\":\"A1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Racks cannot be updated"));
        mvc.perform(post("/pharmacy/inventory/location/bulk")
                        .principal(staff("racks:read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"DR100\"],\"rack\":\"A1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Racks cannot be updated"));
        verify(stock, never()).updateLocation(any(), any(), any(), any());
    }

    @Test
    void blankAndDuplicateRacksAreRefused() throws Exception {
        mvc.perform(post("/pharmacy/racks")
                        .principal(staff("racks:create"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rack\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Rack code is required"));

        Mockito.when(stock.rackCodes(PHARMACY)).thenReturn(List.of("A3-12"));
        mvc.perform(post("/pharmacy/racks")
                        .principal(staff("racks:create"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rack\":\"a3-12\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("That rack already exists"));
        verify(stock, never()).insertRack(any(), any());
    }

    @Test
    void placingAProductKeepsTheSecondSlot() throws Exception {
        StockStore.ProductRow current = product(null, "C2");
        StockStore.ProductRow saved = product("A1", "C2");
        Mockito.when(stock.product(PHARMACY, "DR100")).thenReturn(Optional.of(current), Optional.of(saved));
        Mockito.when(stock.updateLocation(PHARMACY, "DR100", "A1", "C2")).thenReturn(1);
        Mockito.when(stock.batches(PHARMACY)).thenReturn(List.of());

        mvc.perform(patch("/pharmacy/inventory/DR100/location")
                        .principal(staff("racks:update"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rack\":\"a1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.rack").value("A1"))
                .andExpect(jsonPath("$.product.rackAlt").value("C2"));
    }

    @Test
    void updateIsRequiredToSetAnOnlinePrice() throws Exception {
        mvc.perform(put("/pharmacy/inventory/DR100/online-price")
                        .principal(staff("inventory:read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"onlineMrp\":\"120.00\",\"onlinePrice\":\"99.00\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Inventory cannot be updated"));
        verify(stock, never()).updateOnlinePrice(any(), any(), any(), any());
    }

    @Test
    void anOnlinePriceAboveMrpIsRejected() throws Exception {
        mvc.perform(put("/pharmacy/inventory/DR100/online-price")
                        .principal(staff("inventory:update"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"onlineMrp\":\"90.00\",\"onlinePrice\":\"99.00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Online price cannot exceed online MRP"));
        verify(stock, never()).updateOnlinePrice(any(), any(), any(), any());
    }

    @Test
    void settingAnOnlinePriceReturnsTheProduct() throws Exception {
        StockStore.ProductRow saved = priced("120.00", "99.00");
        Mockito.when(stock.updateOnlinePrice(eq(PHARMACY), eq("DR100"), any(), any()))
                .thenReturn(1);
        Mockito.when(stock.product(PHARMACY, "DR100")).thenReturn(Optional.of(saved));
        Mockito.when(stock.batches(PHARMACY)).thenReturn(List.of());

        mvc.perform(put("/pharmacy/inventory/DR100/online-price")
                        .principal(staff("inventory:update"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"onlineMrp\":\"120.00\",\"onlinePrice\":\"99.00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.onlineMrp").value("120.00"))
                .andExpect(jsonPath("$.product.onlinePrice").value("99.00"));
    }

    @Test
    void clearingAnOnlinePriceReturnsNullPrices() throws Exception {
        StockStore.ProductRow cleared = product("A1", null);
        Mockito.when(stock.clearOnlinePrice(PHARMACY, "DR100")).thenReturn(1);
        Mockito.when(stock.product(PHARMACY, "DR100")).thenReturn(Optional.of(cleared));
        Mockito.when(stock.batches(PHARMACY)).thenReturn(List.of());

        mvc.perform(delete("/pharmacy/inventory/DR100/online-price").principal(staff("inventory:update")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.onlineMrp").value(nullValue()))
                .andExpect(jsonPath("$.product.onlinePrice").value(nullValue()));
    }

    private static StockStore.ProductRow product(String rack, String rackAlt) {
        return new StockStore.ProductRow(
                "DR100", "Crocin", null, null, null, null, null, 20, rack, rackAlt, false, null, null);
    }

    private static StockStore.ProductRow priced(String onlineMrp, String onlinePrice) {
        return new StockStore.ProductRow(
                "DR100",
                "Crocin",
                null,
                null,
                null,
                null,
                null,
                20,
                null,
                null,
                false,
                new BigDecimal(onlineMrp),
                new BigDecimal(onlinePrice));
    }

    private static AccountAuthentication staff(String... keys) {
        AccountPrincipal principal = new AccountPrincipal(
                UUID.randomUUID(),
                "919800000000",
                AccountType.PHARMACY,
                "Ada",
                false,
                PHARMACY,
                PharmacyRole.STAFF,
                VerificationStatus.APPROVED,
                null,
                null,
                Set.of(),
                Set.of(keys),
                true,
                null);
        return new AccountAuthentication(principal);
    }
}
