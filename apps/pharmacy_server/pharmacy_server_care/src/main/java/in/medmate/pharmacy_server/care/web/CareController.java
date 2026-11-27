package in.medmate.pharmacy_server.care.web;

import com.fasterxml.jackson.databind.JsonNode;
import in.medmate.pharmacy_server.identity.web.Current;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "prod", "identity-it"})
@ConditionalOnProperty(prefix = "medmate.catalog", name = "url")
public class CareController {

    private final CareService care;

    public CareController(CareService care) {
        this.care = care;
    }

    @GetMapping("/api/v1/customer/family")
    public CareViews.MemberList family(Authentication authentication) {
        return care.family(Current.require(authentication));
    }

    @PostMapping("/api/v1/customer/family")
    public ResponseEntity<CareViews.MemberOne> createMember(Authentication authentication, @RequestBody JsonNode body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(care.createMember(Current.require(authentication), body));
    }

    @GetMapping("/api/v1/customer/family/{id}")
    public CareViews.MemberOne member(Authentication authentication, @PathVariable UUID id) {
        return care.member(Current.require(authentication), id);
    }

    @PatchMapping("/api/v1/customer/family/{id}")
    public CareViews.MemberOne updateMember(
            Authentication authentication, @PathVariable UUID id, @RequestBody JsonNode body) {
        return care.updateMember(Current.require(authentication), id, body);
    }

    @GetMapping("/api/v1/customer/dosage")
    public CareViews.Board dosage(
            Authentication authentication,
            @RequestParam(required = false) String memberId,
            @RequestParam(required = false) String date) {
        return care.dosage(Current.require(authentication), memberId, date);
    }

    @PostMapping("/api/v1/customer/dosage/doses/{id}/taken")
    public CareViews.DoseResponse taken(Authentication authentication, @PathVariable UUID id) {
        return care.markDose(Current.require(authentication), id, "taken");
    }

    @PostMapping("/api/v1/customer/dosage/doses/{id}/skipped")
    public CareViews.DoseResponse skipped(Authentication authentication, @PathVariable UUID id) {
        return care.markDose(Current.require(authentication), id, "skipped");
    }

    @PostMapping("/api/v1/customer/dosage/doses/{id}/pending")
    public CareViews.DoseResponse pending(Authentication authentication, @PathVariable UUID id) {
        return care.markDose(Current.require(authentication), id, "pending");
    }

    @PostMapping("/api/v1/customer/medicines")
    public ResponseEntity<CareViews.MedicineOne> createMedicine(
            Authentication authentication, @RequestBody JsonNode body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(care.createMedicine(Current.require(authentication), body));
    }

    @GetMapping("/api/v1/customer/medicines/{id}")
    public CareViews.MedicineOne medicine(Authentication authentication, @PathVariable UUID id) {
        return care.medicine(Current.require(authentication), id);
    }

    @PatchMapping("/api/v1/customer/medicines/{id}")
    public CareViews.MedicineOne updateMedicine(
            Authentication authentication, @PathVariable UUID id, @RequestBody JsonNode body) {
        return care.updateMedicine(Current.require(authentication), id, body);
    }

    @GetMapping("/api/v1/customer/prescriptions")
    public CareViews.PrescriptionList prescriptions(Authentication authentication) {
        return care.prescriptions(Current.require(authentication));
    }

    @GetMapping("/api/v1/customer/prescriptions/{id}")
    public CareViews.PrescriptionOne prescription(Authentication authentication, @PathVariable UUID id) {
        return care.prescription(Current.require(authentication), id);
    }

    @PostMapping("/api/v1/customer/prescriptions/{id}/schedules")
    public CareViews.Scheduled schedule(
            Authentication authentication, @PathVariable UUID id, @RequestBody JsonNode body) {
        return care.schedule(Current.require(authentication), id, body);
    }
}
