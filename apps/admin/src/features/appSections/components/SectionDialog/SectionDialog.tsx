import { useEffect, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { Heading } from "@/components/ui/Heading";
import { Input, inputVariants } from "@/components/ui/Input";
import { Stack } from "@/components/ui/Stack";
import { Text } from "@/components/ui/Text";
import { DialogClose, useModalDialog } from "../../dialog";
import {
  blankCategory,
  sectionProblem,
  type AppSection,
  type DraftCategory,
  type SectionDraft,
} from "../../model";

const dialogClass =
  "m-auto max-h-[min(92dvh,860px)] w-[min(720px,calc(100%-2rem))] overflow-auto overscroll-contain rounded-xl border-0 bg-paper p-0 text-ink shadow-pop backdrop:overflow-hidden backdrop:bg-[rgba(13,32,21,0.5)] backdrop:backdrop-blur-[3px] backdrop:overscroll-contain";

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
      <div aria-hidden className="h-1.5 w-full bg-mid" />
      <DialogClose.Provider value={close}>
        <form className="grid gap-4 px-5 py-5" onSubmit={submit}>
          <Heading id="section-dialog-title" level={1} className="text-[1.25em]">
            {creating ? "Add section" : "Edit section"}
          </Heading>
          <Field label="Name" htmlFor="section-name" hint="Shown as the section heading in the app">
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
                ? "JPEG, PNG, or WebP, up to 2 MB. Shown beside the section name."
                : "Leave empty to keep the current image"
            }
          >
            <Input
              id="section-image"
              name="image"
              type="file"
              accept={accept}
              required={creating}
              onChange={(event) => {
                setLocalError(null);
                setCurrent((value) => ({ ...value, image: event.target.files?.[0] ?? null }));
              }}
            />
          </Field>
          <MediaPreview
            file={current.image}
            url={section?.image.url}
            className="size-40 rounded-md object-cover"
          />
          <Field
            label="Banner"
            htmlFor="section-banner"
            hint={
              creating
                ? "JPEG, PNG, or WebP, up to 2 MB. Shown across the top of the section."
                : "Leave empty to keep the current banner"
            }
          >
            <Input
              id="section-banner"
              name="banner"
              type="file"
              accept={accept}
              required={creating}
              onChange={(event) => {
                setLocalError(null);
                setCurrent((value) => ({ ...value, banner: event.target.files?.[0] ?? null }));
              }}
            />
          </Field>
          <MediaPreview
            file={current.banner}
            url={section?.banner.url}
            className="h-28 w-full rounded-md object-cover"
          />
          <Stack className="gap-3">
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
            <Button
              type="button"
              size="sm"
              variant="line"
              className="justify-self-start"
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
          </Stack>
          {!categoriesLoading && categories.length === 0 ? (
            <Text className="text-[13px] text-muted">
              Add a category on a catalog product before creating a section.
            </Text>
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
            <Button type="submit" disabled={saving || categories.length === 0}>
              Save section
            </Button>
          </Stack>
        </form>
      </DialogClose.Provider>
    </dialog>
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

  return (
    <fieldset className="grid gap-3 rounded-lg border border-line p-3">
      <legend className="px-1 text-[13px] font-bold text-ink">Category {index + 1}</legend>
      {removable ? (
        <Button
          type="button"
          size="sm"
          variant="ghost"
          className="justify-self-end"
          onClick={onRemove}
        >
          Remove
        </Button>
      ) : null}
      <Field
        label="Category"
        htmlFor={selectId}
        hint="Categories already used on a drug or FMCG product"
      >
        <select
          id={selectId}
          className={inputVariants()}
          required
          value={category.category}
          disabled={categoriesLoading || choices.length === 0}
          onChange={(event) => onChange({ ...category, category: event.target.value })}
        >
          <option value="">{categoriesLoading ? "Loading categories" : "Choose a category"}</option>
          {choices.map((name) => (
            <option key={name} value={name}>
              {name}
            </option>
          ))}
        </select>
      </Field>
      <Field
        label="Images"
        htmlFor={`section-category-images-${category.key}`}
        hint="JPEG, PNG, or WebP, up to 2 MB each. Add more than one to rotate inside this category."
      >
        <Input
          id={`section-category-images-${category.key}`}
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
      </Field>
      {category.images.length > 0 ? (
        <div className="flex flex-wrap gap-3">
          {category.images.map((image) => (
            <div key={image.key} className="grid justify-items-start gap-1">
              <MediaPreview
                file={image.file}
                url={image.url}
                className="size-16 rounded-md object-cover"
              />
              <Button
                type="button"
                size="sm"
                variant="ghost"
                onClick={() =>
                  onChange({
                    ...category,
                    images: category.images.filter((item) => item.key !== image.key),
                  })
                }
              >
                Remove image
              </Button>
            </div>
          ))}
        </div>
      ) : null}
    </fieldset>
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
  if (!shown) return null;
  return <img src={shown} alt="" className={className} />;
}
