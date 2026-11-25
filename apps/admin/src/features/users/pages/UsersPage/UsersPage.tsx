import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { Heading } from "@/components/ui/Heading";
import { Stack } from "@/components/ui/Stack";
import { Switch } from "@/components/ui/Switch";
import { Text } from "@/components/ui/Text";
import { StaffDialog } from "../../components/StaffDialog/StaffDialog";
import { roleLabels, type PlatformStaff, type StaffDraft } from "../../model";
import { problemMessage } from "../../problemMessage";
import { useCreateStaffMutation, useListStaffQuery, useUpdateStaffMutation } from "../../users.api";

export function UsersPage() {
  const staff = useListStaffQuery();
  const [createStaff, created] = useCreateStaffMutation();
  const [updateStaff, updated] = useUpdateStaffMutation();
  const [editing, setEditing] = useState<PlatformStaff | "new" | null>(null);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [pageError, setPageError] = useState<string | null>(null);
  const rows = staff.data ?? [];

  function closeDialog() {
    setEditing(null);
    setDialogError(null);
  }

  async function save(draft: StaffDraft) {
    setDialogError(null);
    const member = editing !== "new" && editing ? editing : null;
    const result = member
      ? await updateStaff({
          id: member.id,
          body: { displayName: draft.displayName, subtypes: draft.subtypes },
        })
      : await createStaff({
          displayName: draft.displayName,
          phone: draft.phone,
          subtypes: draft.subtypes,
        });
    if ("error" in result) {
      setDialogError(problemMessage(result.error, "Could not save this user"));
      return;
    }
    closeDialog();
  }

  async function setDisabled(member: PlatformStaff, disabled: boolean) {
    setPageError(null);
    const result = await updateStaff({ id: member.id, body: { disabled } });
    if ("error" in result) {
      setPageError(problemMessage(result.error, "Could not update this user"));
    }
  }

  return (
    <Stack className="gap-4">
      <Stack direction="row" align="center" className="justify-between gap-3">
        <Heading level={2} className="text-[18px]">
          Platform staff
        </Heading>
        <Button
          type="button"
          size="sm"
          onClick={() => {
            setDialogError(null);
            setEditing("new");
          }}
        >
          Add user
        </Button>
      </Stack>
      {staff.isError ? (
        <Text className="text-[13.5px] font-bold text-rose">
          {problemMessage(staff.error, "Could not load staff")}
        </Text>
      ) : null}
      {pageError ? <Text className="text-[13.5px] font-bold text-rose">{pageError}</Text> : null}
      {staff.isLoading ? <Text className="text-[13.5px] text-muted">Loading staff</Text> : null}
      {!staff.isLoading && !staff.isError && rows.length === 0 ? (
        <Text className="text-[13.5px] text-muted">No staff yet.</Text>
      ) : null}
      {!staff.isLoading && !staff.isError && rows.length > 0 ? (
        <Stack className="gap-3">
          {rows.map((member) => (
            <article
              key={member.id}
              className="grid gap-3 rounded-lg border border-line bg-paper px-4 py-3.5 sm:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)_auto] sm:items-center"
            >
              <span className="min-w-0">
                <Text className="truncate text-[15px] font-bold text-dark">
                  {member.displayName}
                </Text>
                <Text variant="muted" className="mt-0.5">
                  {member.phone}
                </Text>
              </span>
              <Text className="text-[13.5px] text-ink-2">{roleLabels(member.subtypes)}</Text>
              <span className="flex flex-wrap items-center gap-2 sm:justify-end">
                <Switch
                  checked={!member.disabled}
                  label={
                    member.disabled
                      ? `Enable ${member.displayName}`
                      : `Disable ${member.displayName}`
                  }
                  disabled={updated.isLoading}
                  onClick={() => void setDisabled(member, !member.disabled)}
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  aria-label={`Edit ${member.displayName}`}
                  onClick={() => {
                    setDialogError(null);
                    setEditing(member);
                  }}
                >
                  Edit
                </Button>
              </span>
            </article>
          ))}
        </Stack>
      ) : null}
      {editing ? (
        <StaffDialog
          key={editing === "new" ? "new" : editing.id}
          member={editing === "new" ? null : editing}
          saving={created.isLoading || updated.isLoading}
          error={dialogError}
          onClose={closeDialog}
          onSave={(draft) => void save(draft)}
        />
      ) : null}
    </Stack>
  );
}
