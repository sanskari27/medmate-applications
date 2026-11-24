package in.medmate.pharmacy_server.catalog;

public final class CatalogConflict extends RuntimeException {

    public CatalogConflict() {
        super("Product already exists");
    }
}
