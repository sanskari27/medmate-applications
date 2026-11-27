import { describe, expect, it } from "vitest";
import { couponJson, discountLabel, toFormData, type CouponDraft } from "./model";

function draft(overrides: Partial<CouponDraft> = {}): CouponDraft {
  return {
    code: "welcome10",
    title: "Welcome offer",
    discountType: "pct",
    discountValue: "10",
    maxDiscount: "40",
    minOrder: "200",
    startsAt: "2026-12-01T09:30",
    endsAt: "2026-12-31T18:00",
    maxRedemptions: "100",
    maxPerCustomer: "1",
    pharmacyScope: "selected",
    pharmacyIds: ["pharmacy-1"],
    active: true,
    banner: new File([new Uint8Array([1])], "banner.png", { type: "image/png" }),
    ...overrides,
  };
}

describe("couponJson", () => {
  it("keeps optional limits and drops a flat ceiling", () => {
    const json = couponJson(draft());
    expect(json.code).toBe("welcome10");
    expect(json.maxDiscount).toBe("40");
    expect(json.minOrder).toBe("200");
    expect(json.maxRedemptions).toBe(100);
    expect(json.maxPerCustomer).toBe(1);
    expect(json.pharmacyIds).toEqual(["pharmacy-1"]);
    expect(json.startsAt).toBe(new Date("2026-12-01T09:30").toISOString());
    expect(couponJson(draft({ discountType: "flat", maxDiscount: "40" })).maxDiscount).toBeNull();
    expect(couponJson(draft({ pharmacyScope: "all" })).pharmacyIds).toEqual([]);
  });

  it("packs the coupon json and banner into form data", async () => {
    const body = toFormData(draft({ maxDiscount: "", minOrder: "", startsAt: "", endsAt: "" }));
    const coupon = body.get("coupon");
    expect(coupon).toBeInstanceOf(Blob);
    const json = JSON.parse(await (coupon as Blob).text()) as { maxDiscount: string | null };
    expect(json.maxDiscount).toBeNull();
    expect(body.get("banner")).toBeInstanceOf(File);
  });
});

describe("discountLabel", () => {
  it("describes percent, a ceiling, and a flat amount", () => {
    expect(discountLabel({ discountType: "pct", discountValue: "10.00", maxDiscount: null })).toBe(
      "10%",
    );
    expect(
      discountLabel({ discountType: "pct", discountValue: "10.00", maxDiscount: "40.00" }),
    ).toBe("10% up to ₹40");
    expect(discountLabel({ discountType: "flat", discountValue: "25.50", maxDiscount: null })).toBe(
      "₹25.50",
    );
  });
});
