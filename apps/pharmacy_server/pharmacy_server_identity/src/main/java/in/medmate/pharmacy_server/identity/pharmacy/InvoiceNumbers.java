package in.medmate.pharmacy_server.identity.pharmacy;

import java.util.UUID;

public interface InvoiceNumbers {

    String prefix(UUID pharmacyId);
}
