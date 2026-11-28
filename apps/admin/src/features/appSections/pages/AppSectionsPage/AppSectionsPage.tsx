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
        Home sections for the mobile app. Each section has an image, a banner, and the categories
        shown inside it.
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
        <div className="grid max-w-3xl gap-4">
          {rows.map((section) => (
            <article
              key={section.id}
              className="overflow-hidden rounded-lg border border-line bg-paper"
            >
              <img
                src={section.banner.url}
                alt=""
                width={768}
                height={144}
                className="h-36 w-full object-cover"
              />
              <div className="grid gap-3 p-3">
                <div className="flex items-center gap-3">
                  <img
                    src={section.image.url}
                    alt=""
                    width={64}
                    height={64}
                    className="size-16 rounded-md object-cover"
                  />
                  <div>
                    <Text className="text-[15px] font-bold text-ink">{section.title}</Text>
                    <Text className="text-[12px] text-muted">Position {section.position}</Text>
                  </div>
                </div>
                <div className="grid gap-2 sm:grid-cols-2">
                  {section.categories.map((category) => (
                    <div key={category.id} className="grid gap-2 rounded-md border border-line p-2">
                      <Text className="text-[13px] font-bold text-ink">{category.category}</Text>
                      <div className="flex flex-wrap gap-2">
                        {category.images.map((image) => (
                          <img
                            key={image.id}
                            src={image.url}
                            alt=""
                            width={56}
                            height={56}
                            className="size-14 rounded-md object-cover"
                          />
                        ))}
                      </div>
                    </div>
                  ))}
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
