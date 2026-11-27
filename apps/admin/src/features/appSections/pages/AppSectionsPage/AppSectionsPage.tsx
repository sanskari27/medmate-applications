import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
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
        <Heading level={2} className="text-[18px]">
          App sections
        </Heading>
        <Button
          type="button"
          size="sm"
          onClick={() => {
            setDialogError(null);
            setEditing("new");
          }}
        >
          Add section
        </Button>
      </Stack>
      <Text className="text-[13.5px] text-muted">
        Category tiles for the mobile app. Each tile uses one catalog category and a square image.
      </Text>
      {sections.isError ? (
        <Text role="alert" className="text-[13.5px] font-bold text-rose">
          {problemMessage(sections.error, "Could not load sections")}
        </Text>
      ) : null}
      {sections.isLoading ? (
        <Text className="text-[13.5px] text-muted">Loading sections</Text>
      ) : null}
      {!sections.isLoading && !sections.isError && rows.length === 0 ? (
        <Text className="text-[13.5px] text-muted">No sections yet.</Text>
      ) : null}
      {!sections.isLoading && !sections.isError && rows.length > 0 ? (
        <div className="flex flex-wrap gap-4">
          {rows.map((section) => (
            <article
              key={section.id}
              className="flex w-40 flex-col gap-2 rounded-lg border border-line bg-paper p-3"
            >
              <img
                src={section.image.url}
                alt=""
                width={160}
                height={160}
                className="aspect-square w-full rounded-md object-cover"
              />
              <Text className="text-[13.5px] font-bold text-ink">{section.category}</Text>
              <Text className="text-[12px] text-muted">Position {section.position}</Text>
              <Stack direction="row" className="gap-2">
                <Button type="button" size="sm" variant="line" onClick={() => setEditing(section)}>
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
          title={`Delete ${pendingDelete.category}?`}
          body="The mobile app will stop showing this category tile."
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
