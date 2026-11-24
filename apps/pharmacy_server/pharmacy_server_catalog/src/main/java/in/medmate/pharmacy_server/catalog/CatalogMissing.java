package in.medmate.pharmacy_server.catalog;

public final class CatalogMissing extends RuntimeException {

    public CatalogMissing() {
        super("Product was not found");
    }
}
