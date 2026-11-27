import { useEffect, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { Heading } from "@/components/ui/Heading";
import { Input, inputVariants } from "@/components/ui/Input";
import { Stack } from "@/components/ui/Stack";
import { Text } from "@/components/ui/Text";
import { DialogClose, useModalDialog } from "../../dialog";
import { sectionProblem, type AppSection, type SectionDraft } from "../../model";

const dialogClass =
  "m-auto max-h-[min(92dvh,860px)] w-[min(480px,calc(100%-2rem))] overflow-auto overscroll-contain rounded-xl border-0 bg-paper p-0 text-ink shadow-pop backdrop:overflow-hidden backdrop:bg-[rgba(13,32,21,0.5)] backdrop:backdrop-blur-[3px] backdrop:overscroll-contain";

export function SectionDialog({
  section,
  categories,
  categoriesLoading,
  draft,
  saving,
  error,
  onClose,
  onSave,
}: {
  section: AppSection | null;
  categories: string[];
  categoriesLoading: boolean;
  draft: SectionDraft;
  saving: boolean;
  error: string | null;
  onClose: () => void;
  onSave: (draft: SectionDraft) => void;
}) {
  const creating = section == null;
  const dialogRef = useModalDialog(onClose);
  const [current, setCurrent] = useState(draft);
  const [localError, setLocalError] = useState<string | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const message = localError ?? error;
  const shown = preview ?? section?.image.url ?? null;
  const choices = categories.includes(current.category)
    ? categories
    : current.category
      ? [current.category, ...categories]
      : categories;

  useEffect(() => {
    if (!current.image || typeof URL.createObjectURL !== "function") {
      setPreview(null);
      return;
    }
    const url = URL.createObjectURL(current.image);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [current.image]);

  function close() {
    const dialog = dialogRef.current;
    if (!dialog || typeof dialog.close !== "function") {
      onClose();
      return;
    }
    dialog.close();
    if (dialog.open) onClose();
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const problem = sectionProblem(current, creating);
    if (problem) {
      setLocalError(problem);
      return;
    }
    onSave(current);
  }

  return (
    <dialog ref={dialogRef} aria-labelledby="section-dialog-title" className={dialogClass}>
      <div aria-hidden className="h-1.5 w-full bg-mid" />
      <DialogClose.Provider value={close}>
        <form className="grid gap-4 px-5 py-5" onSubmit={submit}>
          <Heading id="section-dialog-title" level={1} className="text-[1.25em]">
            {creating ? "Add section" : "Edit section"}
          </Heading>
          <Field
            label="Category"
            htmlFor="section-category"
            hint="Categories already used on a drug or FMCG product"
          >
            <select
              id="section-category"
              name="category"
              className={inputVariants()}
              autoFocus
              required
              value={current.category}
              disabled={categoriesLoading || choices.length === 0}
              onChange={(event) => {
                setLocalError(null);
                setCurrent((value) => ({ ...value, category: event.target.value }));
              }}
            >
              <option value="">
                {categoriesLoading ? "Loading categories" : "Choose a category"}
              </option>
              {choices.map((category) => (
                <option key={category} value={category}>
                  {category}
                </option>
              ))}
            </select>
          </Field>
          {!categoriesLoading && choices.length === 0 ? (
            <Text className="text-[13px] text-muted">
              Add a category on a catalog product before creating a section.
            </Text>
          ) : null}
          <Field label="Position" htmlFor="section-position" hint="Lower numbers appear first">
            <Input
              id="section-position"
              name="position"
              inputMode="numeric"
              required
              value={current.position}
              onChange={(event) => {
                setLocalError(null);
                setCurrent((value) => ({ ...value, position: event.target.value }));
              }}
            />
          </Field>
          <Field
            label="Image"
            htmlFor="section-image"
            hint={
              creating
                ? "JPEG, PNG, or WebP, up to 2 MB. Shown as a square."
                : "Leave empty to keep the current image"
            }
          >
            <Input
              id="section-image"
              name="image"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              required={creating}
              onChange={(event) => {
                setLocalError(null);
                setCurrent((value) => ({ ...value, image: event.target.files?.[0] ?? null }));
              }}
            />
          </Field>
          {shown ? (
            <img
              src={shown}
              alt=""
              width={160}
              height={160}
              className="size-40 rounded-md object-cover"
            />
          ) : null}
          {message ? (
            <Text role="alert" className="text-[12.5px] font-bold text-rose">
              {message}
            </Text>
          ) : null}
          <Stack direction="row" className="justify-end gap-2">
            <Button type="button" variant="ghost" onClick={close}>
              Cancel
            </Button>
            <Button type="submit" disabled={saving || choices.length === 0}>
              Save section
            </Button>
          </Stack>
        </form>
      </DialogClose.Provider>
    </dialog>
  );
}
