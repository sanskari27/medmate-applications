import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { Provider } from "react-redux";
import { afterEach, describe, expect, it, vi } from "vitest";
import { createAppStore } from "@/app/store";
import { SESSION_STORAGE_KEY } from "@/constants";
import type { Session } from "@/types";
import { CouponsPage } from "./CouponsPage";

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

const coupon = {
  id: "coupon-1",
  code: "SAVE10",
  title: "Ten percent off",
  discountType: "pct",
  discountValue: "10.00",
  maxDiscount: null,
  minOrder: null,
  startsAt: null,
  endsAt: null,
  maxRedemptions: null,
  maxPerCustomer: null,
  pharmacyScope: "all",
  pharmacyIds: [],
  active: true,
  redemptions: 0,
  banner: { contentType: "image/png", url: "http://127.0.0.1/banner.png" },
  createdAt: "2026-11-27T00:00:00Z",
  updatedAt: "2026-11-27T00:00:00Z",
};

function requestUrl(input: RequestInfo | URL) {
  if (typeof input === "string") return input;
  if (input instanceof URL) return input.toString();
  return input.url;
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  sessionStorage.clear();
});

describe("CouponsPage", () => {
  it("closes the editor after a coupon is saved", async () => {
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ session, pinReset: false }));
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = requestUrl(input);
        if (url.includes("/api/v1/coupons/coupon-1") && init?.method === "PUT") {
          return Response.json({ coupon });
        }
        if (url.includes("/api/v1/coupons/coupon-1")) return Response.json({ coupon });
        if (url.includes("/api/v1/coupons")) {
          return Response.json({
            coupons: [{ ...coupon, pharmacyCount: 0 }],
            page: 1,
            size: 25,
            total: 1,
          });
        }
        return Response.json({ detail: "Not found" }, { status: 404 });
      }),
    );

    render(
      <Provider store={createAppStore()}>
        <CouponsPage />
      </Provider>,
    );

    fireEvent.click(await screen.findByRole("button", { name: "Edit Ten percent off" }));
    fireEvent.click(await screen.findByRole("button", { name: "Save coupon" }));

    await waitFor(() => {
      expect(screen.queryByRole("heading", { name: "Edit coupon" })).toBeNull();
    });
    expect(screen.getByText("Ten percent off")).toBeTruthy();
  });
});
