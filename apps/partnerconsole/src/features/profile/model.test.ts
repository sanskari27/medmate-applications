import { describe, expect, it } from "vitest";
import {
  formFromApplication,
  isDirty,
  needsReview,
  toFormData,
  type ProfileApplication,
  type ProfileForm,
} from "./model";

const application: ProfileApplication = {
  name: "Care Pharmacy",
  tagline: "Open late",
  email: "owner@example.com",
  address: "12 Market Road",
  city: "Bengaluru",
  state: "Karnataka",
  pincode: "560001",
  latitude: 12.9716,
  longitude: 77.5946,
  businessTypes: ["Retail Pharmacy"],
  industry: "Pharmacy & Healthcare",
  registrationType: "Proprietorship",
  pharmacyType: "pharmacy",
  gstin: "29ABCDE1234F1Z5",
  pan: "ABCDE1234F",
  drugLicenceNo: "DL-1",
  fssaiNo: "FSSAI-1",
  tradeLicenceNo: "TL-1",
  enableTds: false,
  enableTcs: false,
  eInvoicing: false,
  pharmacistName: "Asha Rao",
  terms: "Goods once sold",
  extraDetails: [],
  documents: [],
};

function saved(): ProfileForm {
  return formFromApplication(application);
}

describe("profile model", () => {
  it("treats a tagline edit as safe and a licence or pharmacist edit as a review", () => {
    const baseline = saved();
    expect(needsReview(baseline, { ...baseline, tagline: "Open early" })).toBe(false);
    expect(needsReview(baseline, { ...baseline, gstin: "29ABCDE1234F2Z5" })).toBe(true);
    expect(needsReview(baseline, { ...baseline, gstin: "29abcde1234f1z5" })).toBe(false);
    expect(needsReview(baseline, { ...baseline, pharmacistName: "Other Rao" })).toBe(true);
    const signature = new File([new Uint8Array([1])], "sign.png", { type: "image/png" });
    const logo = new File([new Uint8Array([1])], "logo.png", { type: "image/png" });
    expect(needsReview(baseline, { ...baseline, files: { signature } })).toBe(false);
    expect(needsReview(baseline, { ...baseline, files: { logo } })).toBe(false);
    expect(isDirty(baseline, baseline)).toBe(false);
    expect(isDirty(baseline, { ...baseline, tagline: "Open early" })).toBe(true);
    expect(isDirty(baseline, { ...baseline, files: { logo } })).toBe(true);
  });

  it("omits invoice prefix and pharmacy type from the profile body", () => {
    const body = toFormData({ ...saved(), tagline: "Open early" });
    expect(body.get("invoicePrefix")).toBeNull();
    expect(body.get("pharmacyType")).toBeNull();
    expect(body.get("acceptOnlineOrders")).toBeNull();
    expect(body.get("tagline")).toBe("Open early");
    expect(body.get("latitude")).toBe("12.9716");
    expect(body.get("longitude")).toBe("77.5946");
    expect(body.get("gstin")).toBe("29ABCDE1234F1Z5");
    expect(body.has("logo")).toBe(false);
    expect(body.has("terms")).toBe(false);
    expect(body.has("signature")).toBe(false);
    expect(body.get("openingHours")).toBeNull();
  });

  it("sends a week of opening hours after they are edited and does not send them for review", () => {
    const baseline = saved();
    expect(baseline.includeOpeningHours).toBe(false);
    const openingHours = baseline.openingHours.map((hour) =>
      hour.day === "monday"
        ? { ...hour, closed: false, opensAt: "09:00", closesAt: "21:00" }
        : hour,
    );
    const next = { ...baseline, openingHours, includeOpeningHours: true };
    expect(needsReview(baseline, next)).toBe(false);
    expect(isDirty(baseline, next)).toBe(true);
    const sent = JSON.parse(String(toFormData(next).get("openingHours"))) as {
      day: string;
      closed: boolean;
      opensAt: string | null;
      closesAt: string | null;
    }[];
    expect(sent).toHaveLength(7);
    expect(sent[0]).toEqual({ day: "monday", closed: false, opensAt: "09:00", closesAt: "21:00" });
    expect(sent[6]).toEqual({ day: "sunday", closed: true, opensAt: null, closesAt: null });
  });
});
