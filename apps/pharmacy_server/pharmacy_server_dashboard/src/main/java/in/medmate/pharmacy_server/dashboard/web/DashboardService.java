package in.medmate.pharmacy_server.dashboard.web;

import in.medmate.pharmacy_server.dashboard.DashboardRules;
import in.medmate.pharmacy_server.identity.access.Access;
import in.medmate.pharmacy_server.identity.access.AccountPrincipal;
import in.medmate.pharmacy_server.reports.store.ReportStore;
import in.medmate.pharmacy_server.shared.error.ApiProblem;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Profile({"local", "prod", "identity-it"})
public class DashboardService {

    private final ReportStore reports;
    private final Clock clock;

    public DashboardService(ReportStore reports, Clock clock) {
        this.reports = reports;
        this.clock = clock;
    }

    public DashboardRules.Snapshot read(AccountPrincipal actor, String range) {
        if (actor.pharmacyId() == null
                || !Access.pharmacyMember(
                        actor.accountType(),
                        actor.disabled(),
                        actor.pharmacyRole(),
                        actor.pharmacyId(),
                        actor.pharmacyId(),
                        actor.verificationStatus())) {
            throw new ApiProblem(HttpStatus.FORBIDDEN, "Forbidden", "The dashboard cannot be read.");
        }
        LocalDate today = LocalDate.now(clock.withZone(DashboardRules.ZONE));
        DashboardRules.Parsed parsed = DashboardRules.parse(range, today);
        if (parsed.problem() != null) {
            throw new ApiProblem(HttpStatus.BAD_REQUEST, "Bad request", parsed.problem());
        }
        return DashboardRules.build(reports.load(actor.pharmacyId()), parsed, today);
    }
}
