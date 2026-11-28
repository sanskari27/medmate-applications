import { useEffect, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { Heading } from "@/components/ui/Heading";
import { Input, inputVariants } from "@/components/ui/Input";
import { Stack } from "@/components/ui/Stack";
import { Text } from "@/components/ui/Text";
import { cn } from "@/utils/className";
import { DialogClose, useModalDialog } from "../../dialog";
import {
  blankCategory,
  sectionProblem,
  type AppSection,
  type DraftCategory,
  type SectionDraft,
} from "../../model";

const dialogClass =
  "m-auto max-h-[min(92dvh,860px)] w-[min(920px,calc(100%-2rem))] overflow-hidden overscroll-contain rounded-xl border-0 bg-paper p-0 text-ink shadow-pop open:flex open:flex-col backdrop:overflow-hidden backdrop:bg-[rgba(13,32,21,0.5)] backdrop:backdrop-blur-[3px] backdrop:overscroll-contain";

const accept = "image/jpeg,image/png,image/webp";

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

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const problem = sectionProblem(current, creating);
    if (problem) {
      setLocalError(problem);
      return;
    }
    onSave(current);
  }

  function updateCategory(key: string, category: DraftCategory) {
    setLocalError(null);
    setCurrent((value) => ({
      ...value,
      categories: value.categories.map((item) => (item.key === key ? category : item)),
    }));
  }

  return (
    <dialog ref={dialogRef} aria-labelledby="section-dialog-title" className={dialogClass}>
      <div aria-hidden className="h-1.5 w-full shrink-0 bg-mid" />
      <DialogClose.Provider value={close}>
        <form className="flex min-h-0 flex-1 flex-col" onSubmit={submit}>
          <header className="flex items-start justify-between gap-3 border-b border-line px-5 py-4">
            <div className="min-w-0">
              <Heading id="section-dialog-title" level={1} className="text-[1.25em]">
                {creating ? "Add section" : "Edit section"}
              </Heading>
              <Text variant="muted" className="mt-1">
                Name, artwork, and the categories shown in this home row.
              </Text>
            </div>
            <Button type="button" variant="ghost" size="sm" onClick={close}>
              Close
            </Button>
          </header>
          <div className="grid min-h-0 flex-1 content-start gap-5 overflow-auto overscroll-contain px-5 py-5">
            <div className="grid gap-4 sm:grid-cols-[minmax(0,1fr)_7.5rem]">
              <Field
                label="Name"
                htmlFor="section-name"
                hint="Shown as the section heading in the app"
              >
                <Input
                  id="section-name"
                  name="title"
                  autoFocus
                  required
                  value={current.title}
                  onChange={(event) => {
                    setLocalError(null);
                    setCurrent((value) => ({ ...value, title: event.target.value }));
                  }}
                />
              </Field>
              <Field label="Position" htmlFor="section-position" hint="Lower is first">
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
            </div>
            <div className="grid gap-4 md:grid-cols-[minmax(0,1.6fr)_11rem]">
              <MediaField
                id="section-banner"
                name="banner"
                label="Banner"
                hint={
                  creating
                    ? "JPEG, PNG, or WebP, up to 2 MB. Wide image across the top of the row."
                    : "Leave empty to keep the current banner"
                }
                required={creating}
                file={current.banner}
                url={section?.banner.url}
                frameClass="h-36 w-full"
                onChange={(file) => {
                  setLocalError(null);
                  setCurrent((value) => ({ ...value, banner: file }));
                }}
              />
              <MediaField
                id="section-image"
                name="image"
                label="Image"
                hint={creating ? "Square icon beside the name." : "Leave empty to keep it"}
                required={creating}
                file={current.image}
                url={section?.image.url}
                frameClass="size-28"
                onChange={(file) => {
                  setLocalError(null);
                  setCurrent((value) => ({ ...value, image: file }));
                }}
              />
            </div>
            <section className="grid gap-3">
              <div className="flex flex-wrap items-end justify-between gap-2">
                <div>
                  <Heading level={3} className="text-[15px]">
                    Categories
                  </Heading>
                  <Text variant="muted" className="mt-0.5">
                    Each category needs at least one image. Extra images rotate in the app.
                  </Text>
                </div>
                <Button
                  type="button"
                  size="sm"
                  variant="line"
                  onClick={() => {
                    setLocalError(null);
                    setCurrent((value) => ({
                      ...value,
                      categories: [...value.categories, blankCategory()],
                    }));
                  }}
                >
                  Add category
                </Button>
              </div>
              {current.categories.map((category, index) => (
                <CategoryFields
                  key={category.key}
                  index={index}
                  category={category}
                  categories={categories}
                  taken={current.categories
                    .filter((item) => item.key !== category.key)
                    .map((item) => item.category)}
                  categoriesLoading={categoriesLoading}
                  removable={current.categories.length > 1}
                  onChange={(next) => updateCategory(category.key, next)}
                  onRemove={() => {
                    setLocalError(null);
                    setCurrent((value) => ({
                      ...value,
                      categories: value.categories.filter((item) => item.key !== category.key),
                    }));
                  }}
                />
              ))}
              {!categoriesLoading && categories.length === 0 ? (
                <Text className="text-[13px] text-muted">
                  Add a category on a catalog product before creating a section.
                </Text>
              ) : null}
            </section>
          </div>
          <footer className="flex flex-wrap items-center justify-end gap-3 border-t border-line px-5 py-3">
            {message ? (
              <Text role="alert" className="mr-auto text-[12.5px] font-bold text-rose">
                {message}
              </Text>
            ) : null}
            <Stack direction="row" className="gap-2">
              <Button type="button" variant="ghost" onClick={close}>
                Cancel
              </Button>
              <Button type="submit" disabled={saving || categories.length === 0}>
                Save section
              </Button>
            </Stack>
          </footer>
        </form>
      </DialogClose.Provider>
    </dialog>
  );
}

function MediaField({
  id,
  name,
  label,
  hint,
  required,
  file,
  url,
  frameClass,
  onChange,
}: {
  id: string;
  name: string;
  label: string;
  hint: string;
  required: boolean;
  file: File | null;
  url?: string | null;
  frameClass: string;
  onChange: (file: File | null) => void;
}) {
  return (
    <Field label={label} htmlFor={id} hint={hint}>
      <div className="relative overflow-hidden rounded-md border border-dashed border-line-2 bg-canvas">
        <MediaPreview file={file} url={url} className={cn(frameClass, "object-cover")} />
        <Input
          id={id}
          name={name}
          type="file"
          accept={accept}
          required={required}
          className="absolute inset-0 h-full cursor-pointer opacity-0 file:hidden"
          onChange={(event) => onChange(event.target.files?.[0] ?? null)}
        />
        <span className="pointer-events-none absolute right-2 bottom-2 rounded-xs bg-paper/95 px-2 py-1 text-[11px] font-bold text-ink-2 shadow-sm">
          {file || url ? "Replace" : "Upload"}
        </span>
      </div>
    </Field>
  );
}

function CategoryFields({
  index,
  category,
  categories,
  taken,
  categoriesLoading,
  removable,
  onChange,
  onRemove,
}: {
  index: number;
  category: DraftCategory;
  categories: string[];
  taken: string[];
  categoriesLoading: boolean;
  removable: boolean;
  onChange: (category: DraftCategory) => void;
  onRemove: () => void;
}) {
  const blocked = new Set(taken.filter(Boolean));
  const available = categories.filter((name) => !blocked.has(name));
  const choices =
    category.category && !available.includes(category.category)
      ? [category.category, ...available]
      : available;
  const selectId = `section-category-${category.key}`;
  const imagesId = `section-category-images-${category.key}`;

  return (
    <div className="grid gap-3 rounded-lg border border-line bg-paper p-3">
      <div className="flex items-start gap-3">
        <span
          aria-hidden
          className="mt-8 grid size-6 shrink-0 place-items-center rounded-full bg-wash text-[12px] font-bold text-mid"
        >
          {index + 1}
        </span>
        <Field
          className="min-w-0 flex-1"
          label="Category"
          htmlFor={selectId}
          hint="Categories already used on a drug or FMCG product"
        >
          <select
            id={selectId}
            data-slot="select"
            className={inputVariants()}
            required
            value={category.category}
            disabled={categoriesLoading || choices.length === 0}
            onChange={(event) => onChange({ ...category, category: event.target.value })}
          >
            <option value="">
              {categoriesLoading ? "Loading categories" : "Choose a category"}
            </option>
            {choices.map((name) => (
              <option key={name} value={name}>
                {name}
              </option>
            ))}
          </select>
        </Field>
        {removable ? (
          <Button type="button" size="sm" variant="ghost" className="mt-7" onClick={onRemove}>
            Remove
          </Button>
        ) : null}
      </div>
      <div className="grid gap-1.5 pl-9">
        <label htmlFor={imagesId} className="text-[11.5px] font-bold tracking-wide text-ink-2">
          Images
        </label>
        <div className="flex flex-wrap items-start gap-2">
          {category.images.map((image) => (
            <div key={image.key} className="relative">
              <MediaPreview
                file={image.file}
                url={image.url}
                className="size-16 rounded-md object-cover"
              />
              <Button
                type="button"
                size="sm"
                variant="rose"
                aria-label="Remove image"
                className="absolute -top-2 -right-2 size-6 rounded-full p-0"
                onClick={() =>
                  onChange({
                    ...category,
                    images: category.images.filter((item) => item.key !== image.key),
                  })
                }
              >
                ×
              </Button>
            </div>
          ))}
          <label
            htmlFor={imagesId}
            className="grid size-16 cursor-pointer place-items-center rounded-md border border-dashed border-line-2 bg-canvas text-[12px] font-bold text-mid transition-colors duration-200 hover:border-mid hover:bg-wash"
          >
            Add
            <input
              id={imagesId}
              className="sr-only"
              type="file"
              accept={accept}
              multiple
              onChange={(event) => {
                const files = Array.from(event.target.files ?? []);
                event.target.value = "";
                if (files.length === 0) return;
                onChange({
                  ...category,
                  images: [
                    ...category.images,
                    ...files.map((file) => ({
                      key: crypto.randomUUID(),
                      id: null,
                      file,
                      url: null,
                    })),
                  ],
                });
              }}
            />
          </label>
        </div>
        <Text variant="caption">JPEG, PNG, or WebP, up to 2 MB each.</Text>
      </div>
    </div>
  );
}

function MediaPreview({
  file,
  url,
  className,
}: {
  file: File | null;
  url?: string | null;
  className: string;
}) {
  const [local, setLocal] = useState<string | null>(null);
  useEffect(() => {
    if (!file || typeof URL.createObjectURL !== "function") {
      setLocal(null);
      return;
    }
    const next = URL.createObjectURL(file);
    setLocal(next);
    return () => URL.revokeObjectURL(next);
  }, [file]);
  const shown = local ?? url;
  if (!shown) {
    return (
      <div
        className={cn(
          className,
          "grid place-items-center bg-canvas text-[12px] font-bold text-muted",
        )}
      >
        No image
      </div>
    );
  }
  return <img src={shown} alt="" className={className} />;
}
