import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { Provider } from "react-redux";
import { afterEach, describe, expect, it, vi } from "vitest";
import { createAppStore } from "@/app/store";
import { SESSION_STORAGE_KEY } from "@/constants";
import type { Session } from "@/types";
import type { CouponDraft } from "../../model";
import { CouponDialog } from "./CouponDialog";

const session: Session = {
  accessToken: "access",
  refreshToken: "refresh",
  expiresIn: 900,
  account: {
    id: "account-id",
    accountType: "admin",
    phone: "+919876543210",
    displayName: "Ada",
    pharmacyId: null,
    pharmacyRole: null,
    verificationStatus: null,
    rejection: null,
    subtypes: ["sales"],
    permissions: [],
    pinSetupRequired: false,
  },
};

function renderDialog(onSave: (draft: CouponDraft) => void) {
  sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ session, pinReset: false }));
  return render(
    <Provider store={createAppStore()}>
      <CouponDialog coupon={null} saving={false} error={null} onClose={() => {}} onSave={onSave} />
    </Provider>,
  );
}

function banner() {
  return new File([new Uint8Array([1, 2, 3])], "banner.png", { type: "image/png" });
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  sessionStorage.clear();
});

async function opened() {
  expect(await screen.findByRole("button", { name: "Save coupon" })).toBeTruthy();
}

describe("CouponDialog", () => {
  it("saves a percent coupon with a banner", async () => {
    const onSave = vi.fn();
    renderDialog(onSave);
    await opened();
    fireEvent.change(screen.getByLabelText("Code"), { target: { value: "WELCOME10" } });
    fireEvent.change(screen.getByLabelText("Title"), { target: { value: "Welcome offer" } });
    fireEvent.change(screen.getByLabelText("Discount"), { target: { value: "10" } });
    fireEvent.change(screen.getByLabelText("Banner"), { target: { files: [banner()] } });
    fireEvent.click(screen.getByRole("button", { name: "Save coupon" }));
    expect(onSave).toHaveBeenCalledOnce();
    const draft = onSave.mock.calls[0][0] as CouponDraft;
    expect(draft.code).toBe("WELCOME10");
    expect(draft.title).toBe("Welcome offer");
    expect(draft.discountType).toBe("pct");
    expect(draft.discountValue).toBe("10");
    expect(draft.banner?.name).toBe("banner.png");
    expect(draft.pharmacyScope).toBe("all");
    expect(draft.active).toBe(true);
  });

  it("asks for a banner before saving", async () => {
    const onSave = vi.fn();
    renderDialog(onSave);
    await opened();
    fireEvent.change(screen.getByLabelText("Code"), { target: { value: "WELCOME10" } });
    fireEvent.change(screen.getByLabelText("Title"), { target: { value: "Welcome offer" } });
    fireEvent.change(screen.getByLabelText("Discount"), { target: { value: "10" } });
    fireEvent.click(screen.getByRole("button", { name: "Save coupon" }));
    expect(screen.getByRole("alert").textContent).toBe("Banner is required");
    expect(onSave).not.toHaveBeenCalled();
  });

  it("saves a coupon with limits and selected pharmacies", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(
        async () =>
          new Response(
            JSON.stringify({
              owners: [{ pharmacyId: "pharmacy-1", name: "Care Pharmacy", phone: "9886012345" }],
              page: 1,
              size: 20,
              total: 1,
            }),
            { status: 200, headers: { "content-type": "application/json" } },
          ),
      ),
    );
    const onSave = vi.fn();
    renderDialog(onSave);
    await opened();
    fireEvent.change(screen.getByLabelText("Code"), { target: { value: "FESTIVE15" } });
    fireEvent.change(screen.getByLabelText("Title"), { target: { value: "Festive offer" } });
    fireEvent.change(screen.getByLabelText("Discount"), { target: { value: "15" } });
    fireEvent.change(screen.getByLabelText("Maximum discount"), { target: { value: "40" } });
    fireEvent.change(screen.getByLabelText("Minimum order"), { target: { value: "200" } });
    fireEvent.change(screen.getByLabelText("Starts"), { target: { value: "2026-12-01T09:30" } });
    fireEvent.change(screen.getByLabelText("Ends"), { target: { value: "2026-12-31T18:00" } });
    fireEvent.change(screen.getByLabelText("Total uses"), { target: { value: "100" } });
    fireEvent.change(screen.getByLabelText("Uses per customer"), { target: { value: "1" } });
    fireEvent.click(screen.getByRole("radio", { name: "Selected pharmacies" }));
    fireEvent.click(await screen.findByRole("checkbox", { name: "Care Pharmacy" }));
    fireEvent.change(screen.getByLabelText("Banner"), { target: { files: [banner()] } });
    fireEvent.click(screen.getByRole("switch", { name: "Active" }));
    fireEvent.click(screen.getByRole("button", { name: "Save coupon" }));
    await waitFor(() => expect(onSave).toHaveBeenCalledOnce());
    const draft = onSave.mock.calls[0][0] as CouponDraft;
    expect(draft.maxDiscount).toBe("40");
    expect(draft.minOrder).toBe("200");
    expect(draft.startsAt).toBe("2026-12-01T09:30");
    expect(draft.endsAt).toBe("2026-12-31T18:00");
    expect(draft.maxRedemptions).toBe("100");
    expect(draft.maxPerCustomer).toBe("1");
    expect(draft.pharmacyScope).toBe("selected");
    expect(draft.pharmacyIds).toEqual(["pharmacy-1"]);
    expect(draft.active).toBe(false);
  });
});
