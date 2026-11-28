import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { EmptyState } from "@/components/feedback/EmptyState";
import { Heading } from "@/components/ui/Heading";
import { Stack } from "@/components/ui/Stack";
import { Text } from "@/components/ui/Text";
import { SectionDialog } from "../../components/SectionDialog/SectionDialog";
import {
  useDeleteAppSectionMutation,
  useListAppSectionsQuery,
  useListCatalogCategoriesQuery,
  useSaveAppSectionMutation,
} from "../../appSections.api";
import { blankDraft, draftFrom, toFormData, type AppSection, type SectionDraft } from "../../model";
import { problemMessage } from "../../problemMessage";

export function AppSectionsPage() {
  const sections = useListAppSectionsQuery();
  const categories = useListCatalogCategoriesQuery();
  const [saveSection, saved] = useSaveAppSectionMutation();
  const [deleteSection, deleted] = useDeleteAppSectionMutation();
  const [editing, setEditing] = useState<AppSection | "new" | null>(null);
  const [pendingDelete, setPendingDelete] = useState<AppSection | null>(null);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const rows = sections.data?.sections ?? [];
  const nextPosition = rows.reduce((max, row) => Math.max(max, row.position), -1) + 1;

  function closeDialog() {
    setEditing(null);
    setDialogError(null);
  }

  async function save(draft: SectionDraft) {
    setDialogError(null);
    const id = editing && editing !== "new" ? editing.id : undefined;
    const result = await saveSection({ id, body: toFormData(draft) });
    if ("error" in result) {
      setDialogError(problemMessage(result.error, "Could not save this section"));
      return;
    }
    closeDialog();
  }

  async function remove() {
    if (!pendingDelete) return;
    setDeleteError(null);
    const result = await deleteSection(pendingDelete.id);
    if ("error" in result) {
      setDeleteError(problemMessage(result.error, "Could not delete this section"));
      return;
    }
    setPendingDelete(null);
  }

  return (
    <Stack className="gap-4">
      <Stack direction="row" align="center" className="justify-between gap-3">
        <div className="min-w-0">
          <Heading level={2} className="text-[18px]">
            App sections
          </Heading>
          <Text variant="muted" className="mt-0.5">
            Home rows in the mobile app, in the order shoppers see them.
          </Text>
        </div>
        <Button
          type="button"
          size="sm"
          className="shrink-0"
          onClick={() => {
            setDialogError(null);
            setEditing("new");
          }}
        >
          Add section
        </Button>
      </Stack>
      {sections.isError ? (
        <Text role="alert" className="text-[13.5px] font-bold text-rose">
          {problemMessage(sections.error, "Could not load sections")}
        </Text>
      ) : null}
      {sections.isLoading ? (
        <Text className="text-[13.5px] text-muted">Loading sections</Text>
      ) : null}
      {!sections.isLoading && !sections.isError && rows.length === 0 ? (
        <EmptyState
          title="No sections yet"
          body="Add a section with a banner, an icon, and the categories that belong in that row."
        />
      ) : null}
      {!sections.isLoading && !sections.isError && rows.length > 0 ? (
        <div className="grid gap-4">
          {rows.map((section) => (
            <article
              key={section.id}
              className="overflow-hidden rounded-lg border border-line bg-paper shadow-sm"
            >
              <div className="relative bg-canvas">
                <img
                  src={section.banner.url}
                  alt=""
                  width={1200}
                  height={280}
                  className="h-32 w-full object-cover sm:h-36"
                />
                <span className="absolute top-3 left-3 rounded-full bg-paper/95 px-2.5 py-1 text-[12px] font-bold text-ink shadow-sm">
                  Position {section.position}
                </span>
              </div>
              <div className="flex flex-wrap items-center gap-3 px-4 py-3">
                <img
                  src={section.image.url}
                  alt=""
                  width={48}
                  height={48}
                  className="size-12 shrink-0 rounded-md border border-line object-cover"
                />
                <div className="min-w-0 flex-1">
                  <Text className="truncate text-[15px] font-bold text-ink">{section.title}</Text>
                  <Text variant="muted" className="mt-0.5">
                    {section.categories.length === 1
                      ? "1 category"
                      : `${section.categories.length} categories`}
                  </Text>
                </div>
                <Stack direction="row" className="gap-2">
                  <Button
                    type="button"
                    size="sm"
                    variant="line"
                    onClick={() => setEditing(section)}
                  >
                    Edit
                  </Button>
                  <Button
                    type="button"
                    size="sm"
                    variant="rose"
                    onClick={() => {
                      setDeleteError(null);
                      setPendingDelete(section);
                    }}
                  >
                    Delete
                  </Button>
                </Stack>
              </div>
              {section.categories.length > 0 ? (
                <ul className="flex gap-2 overflow-x-auto border-t border-line px-4 py-3">
                  {section.categories.map((category) => {
                    const cover = category.images[0];
                    const extra = Math.max(0, category.images.length - 1);
                    return (
                      <li
                        key={category.id}
                        className="flex shrink-0 items-center gap-2 rounded-md bg-canvas px-2 py-1.5"
                      >
                        {cover ? (
                          <img
                            src={cover.url}
                            alt=""
                            width={32}
                            height={32}
                            className="size-8 rounded-xs object-cover"
                          />
                        ) : null}
                        <span className="text-[13px] font-bold text-ink">{category.category}</span>
                        {extra > 0 ? (
                          <span className="text-[12px] font-bold text-muted">+{extra}</span>
                        ) : null}
                      </li>
                    );
                  })}
                </ul>
              ) : null}
            </article>
          ))}
        </div>
      ) : null}
      {editing ? (
        <SectionDialog
          section={editing === "new" ? null : editing}
          categories={categories.data?.categories ?? []}
          categoriesLoading={categories.isLoading}
          draft={editing === "new" ? blankDraft(nextPosition) : draftFrom(editing)}
          saving={saved.isLoading}
          error={dialogError}
          onClose={closeDialog}
          onSave={(draft) => void save(draft)}
        />
      ) : null}
      {pendingDelete ? (
        <ConfirmDialog
          title={`Delete ${pendingDelete.title}?`}
          body="The mobile app will stop showing this section and the categories inside it."
          tone="danger"
          busy={deleted.isLoading}
          error={deleteError}
          onClose={() => setPendingDelete(null)}
        >
          <Button type="button" variant="line" onClick={() => setPendingDelete(null)}>
            Keep
          </Button>
          <Button
            type="button"
            variant="rose"
            disabled={deleted.isLoading}
            onClick={() => void remove()}
          >
            Delete
          </Button>
        </ConfirmDialog>
      ) : null}
    </Stack>
  );
}
