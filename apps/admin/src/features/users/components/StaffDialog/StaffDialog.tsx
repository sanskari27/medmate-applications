import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { Heading } from "@/components/ui/Heading";
import { Input } from "@/components/ui/Input";
import { Stack } from "@/components/ui/Stack";
import { Text } from "@/components/ui/Text";
import { DialogClose, useModalDialog } from "../../dialog";
import {
  isStaffRole,
  roleLabel,
  STAFF_ROLES,
  type PlatformStaff,
  type StaffDraft,
  type StaffRole,
} from "../../model";

const dialogClass =
  "m-auto max-h-[min(92dvh,760px)] w-[min(520px,calc(100%-2rem))] overflow-auto overscroll-contain rounded-xl border-0 bg-paper p-0 text-ink shadow-pop backdrop:overflow-hidden backdrop:bg-[rgba(13,32,21,0.5)] backdrop:backdrop-blur-[3px] backdrop:overscroll-contain";

export function StaffDialog({
  member,
  saving,
  error,
  onClose,
  onSave,
}: {
  member: PlatformStaff | null;
  saving: boolean;
  error: string | null;
  onClose: () => void;
  onSave: (draft: StaffDraft) => void;
}) {
  const creating = member == null;
  const dialogRef = useModalDialog(onClose);
  const [displayName, setDisplayName] = useState(member?.displayName ?? "");
  const [phone, setPhone] = useState("");
  const [roles, setRoles] = useState<StaffRole[]>(() =>
    (member?.subtypes ?? []).filter(isStaffRole),
  );
  const [localError, setLocalError] = useState<string | null>(null);
  const message = localError ?? error;

  function close() {
    const dialog = dialogRef.current;
    if (!dialog || typeof dialog.close !== "function") {
      onClose();
      return;
    }
    dialog.close();
    if (dialog.open) onClose();
  }

  function toggle(role: StaffRole) {
    setLocalError(null);
    setRoles((current) =>
      current.includes(role) ? current.filter((item) => item !== role) : [...current, role],
    );
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const name = displayName.trim();
    if (!name || name.length > 80) {
      setLocalError("Display name is required");
      return;
    }
    const nextPhone = phone.trim();
    if (creating && nextPhone.length === 0) {
      setLocalError("Enter an Indian mobile number");
      return;
    }
    const subtypes = STAFF_ROLES.filter((role) => roles.includes(role));
    if (subtypes.length === 0) {
      setLocalError("Choose a staff subtype");
      return;
    }
    setLocalError(null);
    onSave({ displayName: name, phone: nextPhone, subtypes });
  }

  return (
    <dialog ref={dialogRef} aria-labelledby="staff-dialog-title" className={dialogClass}>
      <div aria-hidden className="h-1.5 w-full bg-mid" />
      <DialogClose.Provider value={close}>
        <form className="grid gap-4 px-5 py-5" onSubmit={submit}>
          <Heading id="staff-dialog-title" level={1} className="text-[1.25em]">
            {creating ? "Add user" : "Edit user"}
          </Heading>
          <Field label="Name" htmlFor="staff-name">
            <Input
              id="staff-name"
              name="displayName"
              autoComplete="name"
              autoFocus
              enterKeyHint={creating ? "next" : "done"}
              maxLength={80}
              required
              value={displayName}
              onChange={(event) => {
                setLocalError(null);
                setDisplayName(event.target.value);
              }}
            />
          </Field>
          {member ? (
            <Field label="Mobile">
              <Text>{member.phone}</Text>
            </Field>
          ) : (
            <Field label="Mobile" htmlFor="staff-phone" hint="Indian mobile number">
              <Input
                id="staff-phone"
                name="phone"
                type="tel"
                inputMode="tel"
                autoComplete="tel"
                enterKeyHint="next"
                placeholder="98860 12345"
                required
                value={phone}
                onChange={(event) => {
                  setLocalError(null);
                  setPhone(event.target.value);
                }}
              />
            </Field>
          )}
          <fieldset className="grid gap-2 border-0 p-0">
            <legend className="mb-1 text-[13px] font-bold text-dark">Roles</legend>
            {STAFF_ROLES.map((role) => (
              <label key={role} className="flex items-center gap-2 text-[13.5px] text-ink">
                <input
                  type="checkbox"
                  name="subtypes"
                  value={role}
                  checked={roles.includes(role)}
                  className="size-4 accent-mid"
                  onChange={() => toggle(role)}
                />
                {roleLabel(role)}
              </label>
            ))}
          </fieldset>
          {message ? (
            <Text id="staff-dialog-error" className="text-[12.5px] font-bold text-rose">
              {message}
            </Text>
          ) : null}
          <Stack direction="row" className="justify-end gap-2">
            <Button type="button" variant="ghost" onClick={close}>
              Cancel
            </Button>
            <Button type="submit" disabled={saving}>
              Save
            </Button>
          </Stack>
        </form>
      </DialogClose.Provider>
    </dialog>
  );
}
