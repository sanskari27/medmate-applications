package in.medmate.pharmacy_server.catalog;

import java.util.List;
import java.util.Optional;

public interface CatalogStore {

    CatalogDocuments.SearchResponse search(String query, int limit);

    CatalogDocuments.CatalogList list(CatalogRules.Listing listing);

    CatalogDocuments.AvailablePage available(
            List<String> productIds, String category, String query, int page, int size);

    List<String> categories();

    Optional<String> canonicalCategory(String category);

    Found findMany(List<String> ids);

    CatalogDocuments.DrugDetail createDrug(CatalogDocuments.DrugDetail draft);

    CatalogDocuments.DrugDetail updateDrug(CatalogDocuments.DrugDetail draft);

    CatalogDocuments.FmcgDetail createFmcg(CatalogDocuments.FmcgDetail draft);

    CatalogDocuments.FmcgDetail updateFmcg(CatalogDocuments.FmcgDetail draft);

    record Found(
            List<CatalogDocuments.DrugDetail> drug, List<CatalogDocuments.FmcgDetail> fmcg, List<String> missing) {}
}
