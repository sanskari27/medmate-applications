package in.medmate.pharmacy_server.stock;

public final class CatalogUnavailable extends RuntimeException {

    public CatalogUnavailable() {
        super("Medicine catalog is unavailable");
    }
}
