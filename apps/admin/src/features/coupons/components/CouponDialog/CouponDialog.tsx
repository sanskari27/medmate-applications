import { useEffect, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { Heading } from "@/components/ui/Heading";
import { Input } from "@/components/ui/Input";
import { Label } from "@/components/ui/Label";
import { Stack } from "@/components/ui/Stack";
import { Switch } from "@/components/ui/Switch";
import { Text } from "@/components/ui/Text";
import { useListCouponPharmaciesQuery } from "../../coupons.api";
import { DialogClose, useModalDialog } from "../../dialog";
import {
  couponProblem,
  draftFrom,
  type CouponDetail,
  type CouponDraft,
  type DiscountType,
  type PharmacyScope,
} from "../../model";

const dialogClass =
  "m-auto max-h-[min(92dvh,860px)] w-[min(640px,calc(100%-2rem))] overflow-auto overscroll-contain rounded-xl border-0 bg-paper p-0 text-ink shadow-pop backdrop:overflow-hidden backdrop:bg-[rgba(13,32,21,0.5)] backdrop:backdrop-blur-[3px] backdrop:overscroll-contain";

export function CouponDialog({
  coupon,
  saving,
  error,
  onClose,
  onSave,
}: {
  coupon: CouponDetail | null;
  saving: boolean;
  error: string | null;
  onClose: () => void;
  onSave: (draft: CouponDraft) => void;
}) {
  const creating = coupon == null;
  const dialogRef = useModalDialog(onClose);
  const initial = draftFrom(coupon);
  const [draft, setDraft] = useState<CouponDraft>(initial);
  const [localError, setLocalError] = useState<string | null>(null);
  const [invalidId, setInvalidId] = useState<string | null>(null);
  const [pharmacyPage, setPharmacyPage] = useState(1);
  const [pharmacyFilter, setPharmacyFilter] = useState("");
  const [preview, setPreview] = useState<string | null>(null);
  const pharmacies = useListCouponPharmaciesQuery(
    { page: pharmacyPage, size: 20 },
    { skip: draft.pharmacyScope !== "selected" },
  );
  const message = localError ?? error;
  const shownBanner = preview ?? coupon?.banner.url ?? null;

  useEffect(() => {
    if (!draft.banner || typeof URL.createObjectURL !== "function") {
      setPreview(null);
      return;
    }
    const url = URL.createObjectURL(draft.banner);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [draft.banner]);

  function close() {
    const dialog = dialogRef.current;
    if (!dialog || typeof dialog.close !== "function") {
      onClose();
      return;
    }
    dialog.close();
    if (dialog.open) onClose();
  }

  function patch(next: Partial<CouponDraft>) {
    setLocalError(null);
    setInvalidId(null);
    setDraft((current) => ({ ...current, ...next }));
  }

  function togglePharmacy(id: string) {
    setDraft((current) => {
      const selected = current.pharmacyIds.includes(id)
        ? current.pharmacyIds.filter((item) => item !== id)
        : [...current.pharmacyIds, id];
      return { ...current, pharmacyIds: selected };
    });
    setLocalError(null);
    setInvalidId(null);
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const problem = couponProblem(draft, creating);
    if (problem) {
      setLocalError(problem.message);
      setInvalidId(problem.id);
      document.getElementById(problem.id)?.focus();
      return;
    }
    setLocalError(null);
    onSave(draft);
  }

  const owners = (pharmacies.data?.owners ?? []).filter((owner) => {
    const needle = pharmacyFilter.trim().toLowerCase();
    if (!needle) return true;
    return (owner.name ?? owner.phone).toLowerCase().includes(needle);
  });
  const ownerPages = Math.max(1, Math.ceil((pharmacies.data?.total ?? 0) / 20));

  return (
    <dialog ref={dialogRef} aria-labelledby="coupon-dialog-title" className={dialogClass}>
      <div aria-hidden className="h-1.5 w-full bg-mid" />
      <DialogClose.Provider value={close}>
        <form
          className="grid gap-4 px-5 py-5"
          aria-labelledby="coupon-dialog-title"
          onSubmit={submit}
        >
          <Heading id="coupon-dialog-title" level={1} className="text-[1.25em]">
            {creating ? "Add coupon" : "Edit coupon"}
          </Heading>
          <Field label="Code" htmlFor="coupon-code" hint="4–20 letters, numbers, or hyphens">
            <Input
              id="coupon-code"
              name="code"
              autoComplete="off"
              autoCapitalize="characters"
              autoCorrect="off"
              spellCheck={false}
              autoFocus={creating}
              maxLength={20}
              required={creating}
              readOnly={!creating}
              aria-invalid={invalidId === "coupon-code"}
              value={draft.code}
              onChange={(event) => patch({ code: event.target.value })}
            />
          </Field>
          <Field label="Title" htmlFor="coupon-title">
            <Input
              id="coupon-title"
              name="title"
              autoComplete="off"
              maxLength={80}
              required
              autoFocus={!creating}
              aria-invalid={invalidId === "coupon-title"}
              value={draft.title}
              onChange={(event) => patch({ title: event.target.value })}
            />
          </Field>
          <fieldset className="grid gap-2 border-0 p-0">
            <legend className="mb-1 text-[13px] font-bold text-dark">Discount type</legend>
            <label className="flex items-center gap-2 text-[13.5px] text-ink">
              <input
                type="radio"
                name="discountType"
                value="pct"
                className="size-4 accent-mid"
                checked={draft.discountType === "pct"}
                onChange={() => patch({ discountType: "pct" satisfies DiscountType })}
              />
              Percent
            </label>
            <label className="flex items-center gap-2 text-[13.5px] text-ink">
              <input
                type="radio"
                name="discountType"
                value="flat"
                className="size-4 accent-mid"
                checked={draft.discountType === "flat"}
                onChange={() =>
                  patch({ discountType: "flat" satisfies DiscountType, maxDiscount: "" })
                }
              />
              Flat rupees
            </label>
          </fieldset>
          <Field label="Discount" htmlFor="coupon-discount">
            <Input
              id="coupon-discount"
              name="discountValue"
              inputMode="decimal"
              autoComplete="off"
              required
              aria-invalid={invalidId === "coupon-discount"}
              value={draft.discountValue}
              onChange={(event) => patch({ discountValue: event.target.value })}
            />
          </Field>
          {draft.discountType === "pct" ? (
            <Field label="Maximum discount" htmlFor="coupon-cap" hint="Leave blank for no ceiling">
              <Input
                id="coupon-cap"
                name="maxDiscount"
                inputMode="decimal"
                autoComplete="off"
                value={draft.maxDiscount}
                onChange={(event) => patch({ maxDiscount: event.target.value })}
              />
            </Field>
          ) : null}
          <Field label="Minimum order" htmlFor="coupon-minimum" hint="Leave blank for no minimum">
            <Input
              id="coupon-minimum"
              name="minOrder"
              inputMode="decimal"
              autoComplete="off"
              value={draft.minOrder}
              onChange={(event) => patch({ minOrder: event.target.value })}
            />
          </Field>
          <Field label="Starts" htmlFor="coupon-starts" hint="Leave blank to start immediately">
            <Input
              id="coupon-starts"
              name="startsAt"
              type="datetime-local"
              aria-invalid={invalidId === "coupon-starts"}
              value={draft.startsAt}
              onChange={(event) => patch({ startsAt: event.target.value })}
            />
          </Field>
          <Field label="Ends" htmlFor="coupon-ends" hint="Leave blank for no expiry">
            <Input
              id="coupon-ends"
              name="endsAt"
              type="datetime-local"
              aria-invalid={invalidId === "coupon-ends"}
              value={draft.endsAt}
              onChange={(event) => patch({ endsAt: event.target.value })}
            />
          </Field>
          <Field label="Total uses" htmlFor="coupon-uses" hint="Leave blank for unlimited">
            <Input
              id="coupon-uses"
              name="maxRedemptions"
              inputMode="numeric"
              autoComplete="off"
              aria-invalid={invalidId === "coupon-uses"}
              value={draft.maxRedemptions}
              onChange={(event) => patch({ maxRedemptions: event.target.value })}
            />
          </Field>
          <Field
            label="Uses per customer"
            htmlFor="coupon-per-customer"
            hint="Leave blank for unlimited"
          >
            <Input
              id="coupon-per-customer"
              name="maxPerCustomer"
              inputMode="numeric"
              autoComplete="off"
              aria-invalid={invalidId === "coupon-per-customer"}
              value={draft.maxPerCustomer}
              onChange={(event) => patch({ maxPerCustomer: event.target.value })}
            />
          </Field>
          <fieldset className="grid gap-2 border-0 p-0">
            <legend className="mb-1 text-[13px] font-bold text-dark">Pharmacies</legend>
            <label className="flex items-center gap-2 text-[13.5px] text-ink">
              <input
                type="radio"
                name="pharmacyScope"
                value="all"
                className="size-4 accent-mid"
                checked={draft.pharmacyScope === "all"}
                onChange={() => patch({ pharmacyScope: "all" satisfies PharmacyScope })}
              />
              Every pharmacy
            </label>
            <label className="flex items-center gap-2 text-[13.5px] text-ink">
              <input
                type="radio"
                name="pharmacyScope"
                value="selected"
                className="size-4 accent-mid"
                checked={draft.pharmacyScope === "selected"}
                onChange={() => patch({ pharmacyScope: "selected" satisfies PharmacyScope })}
              />
              Selected pharmacies
            </label>
          </fieldset>
          {draft.pharmacyScope === "selected" ? (
            <div role="search">
              <Field label="Find a pharmacy" htmlFor="coupon-pharmacy-filter">
                <Input
                  id="coupon-pharmacy-filter"
                  name="pharmacyFilter"
                  type="search"
                  autoComplete="off"
                  aria-invalid={invalidId === "coupon-pharmacy-filter"}
                  value={pharmacyFilter}
                  onChange={(event) => setPharmacyFilter(event.target.value)}
                />
              </Field>
              <Text className="mt-2 text-[12.5px] text-muted">
                {draft.pharmacyIds.length === 1
                  ? "1 pharmacy selected"
                  : `${draft.pharmacyIds.length} pharmacies selected`}
              </Text>
              {pharmacies.isError ? (
                <Text className="mt-2 text-[12.5px] font-bold text-rose">
                  Could not load pharmacies
                </Text>
              ) : null}
              <div className="mt-2 grid gap-2" role="group" aria-label="Pharmacies">
                {owners.map((owner) => (
                  <label
                    key={owner.pharmacyId}
                    className="flex items-center gap-2 text-[13.5px] text-ink"
                  >
                    <input
                      type="checkbox"
                      name="pharmacyIds"
                      value={owner.pharmacyId}
                      className="size-4 accent-mid"
                      checked={draft.pharmacyIds.includes(owner.pharmacyId)}
                      onChange={() => togglePharmacy(owner.pharmacyId)}
                    />
                    {owner.name ?? owner.phone}
                  </label>
                ))}
              </div>
              <Stack direction="row" className="mt-2 justify-end gap-2">
                <Button
                  type="button"
                  size="sm"
                  variant="ghost"
                  disabled={pharmacyPage <= 1}
                  onClick={() => setPharmacyPage((page) => page - 1)}
                >
                  Previous pharmacies
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant="ghost"
                  disabled={pharmacyPage >= ownerPages}
                  onClick={() => setPharmacyPage((page) => page + 1)}
                >
                  Next pharmacies
                </Button>
              </Stack>
            </div>
          ) : null}
          <Field
            label="Banner"
            htmlFor="coupon-banner"
            hint={
              creating ? "JPEG, PNG, or WebP, up to 2 MB" : "Leave empty to keep the current banner"
            }
          >
            <Input
              id="coupon-banner"
              name="banner"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              aria-invalid={invalidId === "coupon-banner"}
              onChange={(event) => patch({ banner: event.target.files?.[0] ?? null })}
            />
          </Field>
          {shownBanner ? (
            <img
              src={shownBanner}
              alt="Banner preview"
              width={640}
              height={240}
              className="h-36 w-full rounded-sm object-cover"
            />
          ) : null}
          <Stack direction="row" align="center" justify="between" className="gap-3">
            <span className="grid gap-0.5">
              <Label htmlFor="coupon-active">Active</Label>
              <Text variant="caption">Customers can redeem this coupon while it is on</Text>
            </span>
            <Switch
              id="coupon-active"
              checked={draft.active}
              label="Active"
              onClick={() => patch({ active: !draft.active })}
            />
          </Stack>
          {message ? (
            <Text
              id="coupon-dialog-error"
              role="alert"
              className="text-[12.5px] font-bold text-rose"
            >
              {message}
            </Text>
          ) : null}
          <Stack direction="row" className="justify-end gap-2">
            <Button type="button" variant="ghost" onClick={close}>
              Cancel
            </Button>
            <Button type="submit" disabled={saving}>
              Save coupon
            </Button>
          </Stack>
        </form>
      </DialogClose.Provider>
    </dialog>
  );
}
