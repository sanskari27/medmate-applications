package in.medmate.pharmacy_server.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class AddressRulesTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void createRejectsAMissingSheetAndEachInvalidField() {
        assertThat(AddressRules.prepareCreate(null).problem()).isEqualTo("Address details are required");
        ObjectNode body = json.createObjectNode();
        assertThat(AddressRules.prepareCreate(body).problem()).isEqualTo("Save as Home, Work, or Other");
        body.put("label", "Shop");
        assertThat(AddressRules.prepareCreate(body).problem()).isEqualTo("Save as Home, Work, or Other");
        body.put("label", " Home ");
        assertThat(AddressRules.prepareCreate(body).problem()).isEqualTo("Flat, house or building is required");
        body.put("line1", "A".repeat(121));
        assertThat(AddressRules.prepareCreate(body).problem()).isEqualTo("Flat, house or building is too long");
        body.put("line1", " Flat 302 ");
        assertThat(AddressRules.prepareCreate(body).problem()).isEqualTo("Area and street is required");
        body.put("line2", " 7th Cross ");
        body.put("landmark", "L".repeat(121));
        assertThat(AddressRules.prepareCreate(body).problem()).isEqualTo("Landmark is too long");
        body.put("landmark", " Near Cool Joint ");
        assertThat(AddressRules.prepareCreate(body).problem()).isEqualTo("City is required");
        body.put("city", " Bengaluru ");
        body.put("pincode", "400001");
        assertThat(AddressRules.prepareCreate(body).problem())
                .isEqualTo("We deliver across Bengaluru (pincodes starting 560)");
        body.put("pincode", "560011");
        assertThat(AddressRules.prepareCreate(body).problem()).isEqualTo("Pin the delivery spot on the map");
        body.put("latitude", 12.93);
        assertThat(AddressRules.prepareCreate(body).problem()).isEqualTo("A pin needs both latitude and longitude");
        body.put("longitude", 200);
        assertThat(AddressRules.prepareCreate(body).problem()).isEqualTo("Longitude is out of range");
        body.put("longitude", 77.58);
        assertThat(AddressRules.prepareCreate(body).problem()).isEqualTo("Name is required");
        body.put("receiverName", " Priya Sharma ");
        body.put("receiverPhone", "12345");
        assertThat(AddressRules.prepareCreate(body).problem()).isEqualTo("Enter an Indian mobile number");
        body.put("receiverPhone", "98450 12345");
        body.put("callOnArrival", "yes");
        assertThat(AddressRules.prepareCreate(body).problem()).isEqualTo("Flag is not recognised");
        body.put("callOnArrival", true);
        body.put("riderNote", "N".repeat(141));
        assertThat(AddressRules.prepareCreate(body).problem()).isEqualTo("Rider note is too long");
    }

    @Test
    void aValidAddressTrimsTextNormalisesThePhoneAndDefaultsFlags() {
        ObjectNode body = sheet();
        body.put("landmark", "  ");
        body.put("riderNote", "");
        AddressRules.Prepared prepared = AddressRules.prepareCreate(body);
        assertThat(prepared.problem()).isNull();
        AddressRules.Values values = prepared.values();
        assertThat(values.label()).isEqualTo("home");
        assertThat(values.line1()).isEqualTo("Flat 302, Sri Sai Residency");
        assertThat(values.line2()).isEqualTo("7th Cross, Jayanagar 4th Block");
        assertThat(values.landmark()).isNull();
        assertThat(values.city()).isEqualTo("Bengaluru");
        assertThat(values.pincode()).isEqualTo("560011");
        assertThat(values.latitude()).isEqualTo(12.9716);
        assertThat(values.longitude()).isEqualTo(77.5946);
        assertThat(values.receiverName()).isEqualTo("Priya Sharma");
        assertThat(values.receiverPhone()).isEqualTo("+919845012345");
        assertThat(values.callOnArrival()).isFalse();
        assertThat(values.leaveWithSecurity()).isFalse();
        assertThat(values.dontRingBell()).isFalse();
        assertThat(values.liftNotWorking()).isFalse();
        assertThat(values.riderNote()).isNull();
        assertThat(values.selected()).isFalse();
        assertThat(AddressRules.delivers("560011")).isTrue();
        assertThat(AddressRules.delivers("400001")).isFalse();
    }

    @Test
    void updateKeepsOmittedFieldsAndRejectsAClearedPin() {
        AddressRules.Values current = new AddressRules.Values(
                "work",
                "4th Floor",
                "80 Feet Road",
                "Temple",
                "Bengaluru",
                "560095",
                12.9,
                77.6,
                "Priya Sharma",
                "+919845012345",
                true,
                false,
                true,
                false,
                "Ring twice",
                true);
        AddressRules.Prepared kept = AddressRules.prepareUpdate(json.createObjectNode(), current);
        assertThat(kept.problem()).isNull();
        assertThat(kept.values()).isEqualTo(current);

        ObjectNode clear = json.createObjectNode();
        clear.putNull("landmark");
        clear.putNull("riderNote");
        clear.put("selected", false);
        AddressRules.Prepared cleared = AddressRules.prepareUpdate(clear, current);
        assertThat(cleared.problem()).isNull();
        assertThat(cleared.values().landmark()).isNull();
        assertThat(cleared.values().latitude()).isEqualTo(12.9);
        assertThat(cleared.values().longitude()).isEqualTo(77.6);
        assertThat(cleared.values().riderNote()).isNull();
        assertThat(cleared.values().selected()).isFalse();
        assertThat(cleared.values().label()).isEqualTo("work");
        assertThat(cleared.values().callOnArrival()).isTrue();

        ObjectNode wipe = json.createObjectNode();
        wipe.putNull("latitude");
        wipe.putNull("longitude");
        assertThat(AddressRules.prepareUpdate(wipe, current).problem())
                .isEqualTo("Pin the delivery spot on the map");

        ObjectNode half = json.createObjectNode();
        half.putNull("latitude");
        assertThat(AddressRules.prepareUpdate(half, current).problem())
                .isEqualTo("A pin needs both latitude and longitude");
    }

    private ObjectNode sheet() {
        ObjectNode body = json.createObjectNode();
        body.put("label", " Home ");
        body.put("line1", " Flat 302, Sri Sai Residency ");
        body.put("line2", " 7th Cross, Jayanagar 4th Block ");
        body.put("city", " Bengaluru ");
        body.put("pincode", "560 011");
        body.put("latitude", 12.9716);
        body.put("longitude", 77.5946);
        body.put("receiverName", " Priya Sharma ");
        body.put("receiverPhone", "98450 12345");
        return body;
    }
}
