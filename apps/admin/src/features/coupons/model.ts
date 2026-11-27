export type DiscountType = "pct" | "flat";
export type PharmacyScope = "all" | "selected";

export type CouponBanner = {
  contentType: string;
  url: string;
};

export type CouponSummary = {
  id: string;
  code: string;
  title: string;
  discountType: DiscountType;
  discountValue: string;
  maxDiscount: string | null;
  minOrder: string | null;
  startsAt: string | null;
  endsAt: string | null;
  maxRedemptions: number | null;
  maxPerCustomer: number | null;
  pharmacyScope: PharmacyScope;
  pharmacyCount: number;
  active: boolean;
  redemptions: number;
  banner: CouponBanner;
};

export type CouponDetail = Omit<CouponSummary, "pharmacyCount"> & {
  pharmacyIds: string[];
  createdAt: string;
  updatedAt: string;
};

export type CouponList = {
  coupons: CouponSummary[];
  page: number;
  size: number;
  total: number;
};

export type CouponDraft = {
  code: string;
  title: string;
  discountType: DiscountType;
  discountValue: string;
  maxDiscount: string;
  minOrder: string;
  startsAt: string;
  endsAt: string;
  maxRedemptions: string;
  maxPerCustomer: string;
  pharmacyScope: PharmacyScope;
  pharmacyIds: string[];
  active: boolean;
  banner: File | null;
};

export type CouponJson = {
  code: string;
  title: string;
  discountType: DiscountType;
  discountValue: string;
  maxDiscount: string | null;
  minOrder: string | null;
  startsAt: string | null;
  endsAt: string | null;
  maxRedemptions: number | null;
  maxPerCustomer: number | null;
  pharmacyScope: PharmacyScope;
  pharmacyIds: string[];
  active: boolean;
};

export function draftFrom(coupon: CouponDetail | null): CouponDraft {
  return {
    code: coupon?.code ?? "",
    title: coupon?.title ?? "",
    discountType: coupon?.discountType ?? "pct",
    discountValue: coupon?.discountValue ?? "",
    maxDiscount: coupon?.maxDiscount ?? "",
    minOrder: coupon?.minOrder ?? "",
    startsAt: toLocalInput(coupon?.startsAt ?? null),
    endsAt: toLocalInput(coupon?.endsAt ?? null),
    maxRedemptions: coupon?.maxRedemptions?.toString() ?? "",
    maxPerCustomer: coupon?.maxPerCustomer?.toString() ?? "",
    pharmacyScope: coupon?.pharmacyScope ?? "all",
    pharmacyIds: coupon?.pharmacyIds ?? [],
    active: coupon?.active ?? true,
    banner: null,
  };
}

export function couponProblem(
  draft: CouponDraft,
  creating: boolean,
): { id: string; message: string } | null {
  if (!draft.code.trim()) return { id: "coupon-code", message: "Coupon code is required" };
  if (!draft.title.trim()) return { id: "coupon-title", message: "Title is required" };
  const discount = Number(draft.discountValue);
  if (!draft.discountValue.trim() || !Number.isFinite(discount) || discount <= 0) {
    return { id: "coupon-discount", message: "Discount must be greater than zero" };
  }
  if (draft.maxRedemptions.trim() && countOrNull(draft.maxRedemptions) == null) {
    return { id: "coupon-uses", message: "Use limit is invalid" };
  }
  if (draft.maxPerCustomer.trim() && countOrNull(draft.maxPerCustomer) == null) {
    return { id: "coupon-per-customer", message: "Per-customer limit is invalid" };
  }
  if (draft.startsAt && Number.isNaN(new Date(draft.startsAt).getTime())) {
    return { id: "coupon-starts", message: "Start is invalid" };
  }
  if (draft.endsAt && Number.isNaN(new Date(draft.endsAt).getTime())) {
    return { id: "coupon-ends", message: "End is invalid" };
  }
  if (draft.startsAt && draft.endsAt && new Date(draft.endsAt) <= new Date(draft.startsAt)) {
    return { id: "coupon-ends", message: "End must be after the start" };
  }
  if (draft.pharmacyScope === "selected" && draft.pharmacyIds.length === 0) {
    return { id: "coupon-pharmacy-filter", message: "Choose at least one pharmacy" };
  }
  if (creating && !draft.banner) return { id: "coupon-banner", message: "Banner is required" };
  if (draft.banner && !["image/jpeg", "image/png", "image/webp"].includes(draft.banner.type)) {
    return { id: "coupon-banner", message: "Banner must be an image" };
  }
  return null;
}

export function couponJson(draft: CouponDraft): CouponJson {
  return {
    code: draft.code.trim(),
    title: draft.title.trim(),
    discountType: draft.discountType,
    discountValue: draft.discountValue.trim(),
    maxDiscount: draft.discountType === "pct" ? blankToNull(draft.maxDiscount) : null,
    minOrder: blankToNull(draft.minOrder),
    startsAt: draft.startsAt ? new Date(draft.startsAt).toISOString() : null,
    endsAt: draft.endsAt ? new Date(draft.endsAt).toISOString() : null,
    maxRedemptions: countOrNull(draft.maxRedemptions),
    maxPerCustomer: countOrNull(draft.maxPerCustomer),
    pharmacyScope: draft.pharmacyScope,
    pharmacyIds: draft.pharmacyScope === "selected" ? draft.pharmacyIds : [],
    active: draft.active,
  };
}

export function toFormData(draft: CouponDraft): FormData {
  const body = new FormData();
  body.append(
    "coupon",
    new Blob([JSON.stringify(couponJson(draft))], { type: "application/json" }),
  );
  if (draft.banner) body.append("banner", draft.banner);
  return body;
}

export function discountLabel(
  coupon: Pick<CouponSummary, "discountType" | "discountValue" | "maxDiscount">,
) {
  if (coupon.discountType === "pct") {
    const percent = trimMoney(coupon.discountValue);
    return coupon.maxDiscount
      ? `${percent}% up to ₹${trimMoney(coupon.maxDiscount)}`
      : `${percent}%`;
  }
  return `₹${trimMoney(coupon.discountValue)}`;
}

export function windowLabel(startsAt: string | null, endsAt: string | null) {
  if (!startsAt && !endsAt) return "Always";
  const start = startsAt ? formatWhen(startsAt) : "Now";
  const end = endsAt ? formatWhen(endsAt) : "No end";
  return `${start} – ${end}`;
}

export function usesLabel(redemptions: number, max: number | null) {
  return max == null ? String(redemptions) : `${redemptions} / ${max}`;
}

function blankToNull(value: string) {
  const trimmed = value.trim();
  return trimmed.length === 0 ? null : trimmed;
}

function countOrNull(value: string) {
  const trimmed = value.trim();
  if (!trimmed) return null;
  if (!/^\d+$/.test(trimmed)) return null;
  const parsed = Number(trimmed);
  return parsed > 0 ? parsed : null;
}

function trimMoney(value: string) {
  return value.replace(/\.00$/, "");
}

function toLocalInput(value: string | null) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const pad = (part: number) => String(part).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function formatWhen(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString(undefined, {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}
