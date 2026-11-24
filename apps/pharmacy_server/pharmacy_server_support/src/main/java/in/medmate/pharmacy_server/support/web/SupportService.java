package in.medmate.pharmacy_server.support.web;

import in.medmate.pharmacy_server.identity.access.AccountPrincipal;
import in.medmate.pharmacy_server.shared.web.PageQuery;
import java.util.List;
import java.util.UUID;

public interface SupportService {

    List<SupportViews.FaqView> faqs(AccountPrincipal principal);

    SupportViews.FaqView createFaq(AccountPrincipal principal, String question, String answer);

    SupportViews.FaqView updateFaq(
            AccountPrincipal principal, UUID id, String question, String answer, boolean published);

    List<SupportViews.FaqView> reorder(AccountPrincipal principal, List<UUID> ids);

    SupportViews.CaseDetail createCase(
            AccountPrincipal principal, String kind, String subject, String category, String details);

    SupportViews.CaseList cases(AccountPrincipal principal, PageQuery page);

    SupportViews.CaseDetail caseDetail(AccountPrincipal principal, UUID id);

    SupportViews.MessageView message(AccountPrincipal principal, UUID id, String text);

    SupportViews.CaseDetail claim(AccountPrincipal principal, UUID id);

    SupportViews.CaseDetail resolve(AccountPrincipal principal, UUID id);
}
