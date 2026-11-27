import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { Heading } from "@/components/ui/Heading";
import { PageBar } from "@/components/ui/PageBar";
import { Stack } from "@/components/ui/Stack";
import { Text } from "@/components/ui/Text";
import { CouponDialog } from "../../components/CouponDialog/CouponDialog";
import { useListCouponsQuery, useReadCouponQuery, useSaveCouponMutation } from "../../coupons.api";
import { discountLabel, toFormData, usesLabel, windowLabel, type CouponDraft } from "../../model";
import { problemMessage } from "../../problemMessage";

export function CouponsPage() {
  const [page, setPage] = useState(1);
  const coupons = useListCouponsQuery({ page, size: 25 });
  const [saveCoupon, saved] = useSaveCouponMutation();
  const [editing, setEditing] = useState<"new" | string | null>(null);
  const detail = useReadCouponQuery(typeof editing === "string" ? editing : "", {
    skip: editing == null || editing === "new",
  });
  const [dialogError, setDialogError] = useState<string | null>(null);
  const rows = coupons.data?.coupons ?? [];
  const openDialog = editing === "new" || (editing != null && Boolean(detail.data));

  function closeDialog() {
    setEditing(null);
    setDialogError(null);
  }

  async function save(draft: CouponDraft) {
    setDialogError(null);
    const id = editing !== "new" && editing ? editing : undefined;
    const result = await saveCoupon({ id, body: toFormData(draft) });
    if ("error" in result) {
      setDialogError(problemMessage(result.error, "Could not save this coupon"));
      return;
    }
    closeDialog();
  }

  return (
    <Stack className="gap-4">
      <Stack direction="row" align="center" className="justify-between gap-3">
        <Heading level={2} className="text-[18px]">
          Coupons
        </Heading>
        <Button
          type="button"
          size="sm"
          onClick={() => {
            setDialogError(null);
            setEditing("new");
          }}
        >
          Add coupon
        </Button>
      </Stack>
      {coupons.isError ? (
        <Text role="alert" className="text-[13.5px] font-bold text-rose">
          {problemMessage(coupons.error, "Could not load coupons")}
        </Text>
      ) : null}
      {coupons.isLoading ? <Text className="text-[13.5px] text-muted">Loading coupons</Text> : null}
      {editing && editing !== "new" && detail.isLoading ? (
        <Text className="text-[13.5px] text-muted">Loading coupon</Text>
      ) : null}
      {editing && editing !== "new" && detail.isError ? (
        <Text role="alert" className="text-[13.5px] font-bold text-rose">
          {problemMessage(detail.error, "Could not load this coupon")}
        </Text>
      ) : null}
      {!coupons.isLoading && !coupons.isError && rows.length === 0 ? (
        <Text className="text-[13.5px] text-muted">No coupons yet.</Text>
      ) : null}
      {!coupons.isLoading && !coupons.isError && rows.length > 0 ? (
        <Stack className="gap-3">
          {rows.map((coupon) => (
            <article
              key={coupon.id}
              className="grid gap-3 rounded-lg border border-line bg-paper px-4 py-3.5 sm:grid-cols-[5rem_minmax(0,1.4fr)_minmax(0,1fr)_auto] sm:items-center"
            >
              <img
                src={coupon.banner.url}
                alt=""
                width={80}
                height={48}
                className="h-12 w-20 rounded-sm object-cover"
              />
              <span className="min-w-0">
                <Text className="truncate text-[15px] font-bold text-dark">{coupon.title}</Text>
                <Text variant="muted" className="mt-0.5">
                  {coupon.code}
                </Text>
              </span>
              <span className="min-w-0">
                <Text className="text-[13.5px] text-ink-2">{discountLabel(coupon)}</Text>
                <Text variant="muted" className="mt-0.5">
                  {windowLabel(coupon.startsAt, coupon.endsAt)}
                  {" · "}
                  {usesLabel(coupon.redemptions, coupon.maxRedemptions)}
                  {" uses · "}
                  {coupon.pharmacyScope === "all"
                    ? "All pharmacies"
                    : `${coupon.pharmacyCount} pharmacies`}
                  {" · "}
                  {coupon.active ? "Active" : "Paused"}
                </Text>
              </span>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                aria-label={`Edit ${coupon.title}`}
                onClick={() => {
                  setDialogError(null);
                  setEditing(coupon.id);
                }}
              >
                Edit
              </Button>
            </article>
          ))}
          <PageBar
            page={coupons.data?.page ?? page}
            size={coupons.data?.size ?? 25}
            total={coupons.data?.total ?? 0}
            onPage={setPage}
          />
        </Stack>
      ) : null}
      {openDialog ? (
        <CouponDialog
          key={editing === "new" ? "new" : editing}
          coupon={editing === "new" ? null : (detail.data?.coupon ?? null)}
          saving={saved.isLoading}
          error={dialogError}
          onClose={closeDialog}
          onSave={(draft) => void save(draft)}
        />
      ) : null}
    </Stack>
  );
}
