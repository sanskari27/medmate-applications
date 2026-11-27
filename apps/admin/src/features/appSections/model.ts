export type SectionImage = {
  contentType: string;
  url: string;
};

export type AppSection = {
  id: string;
  category: string;
  position: number;
  image: SectionImage;
};

export type AppSectionList = {
  sections: AppSection[];
};

export type SectionDraft = {
  category: string;
  position: string;
  image: File | null;
};

export function blankDraft(position: number): SectionDraft {
  return { category: "", position: String(position), image: null };
}

export function draftFrom(section: AppSection): SectionDraft {
  return { category: section.category, position: String(section.position), image: null };
}

export function sectionProblem(draft: SectionDraft, creating: boolean): string | null {
  if (!draft.category.trim()) return "Category is required";
  if (creating && !draft.image) return "Image is required";
  const position = draft.position.trim();
  if (!/^\d+$/.test(position) || Number(position) > 10000) return "Position is not valid";
  return null;
}

export function toFormData(draft: SectionDraft): FormData {
  const body = new FormData();
  body.append(
    "section",
    new Blob(
      [
        JSON.stringify({
          category: draft.category.trim(),
          position: Number(draft.position.trim() || "0"),
        }),
      ],
      { type: "application/json" },
    ),
  );
  if (draft.image) body.append("image", draft.image);
  return body;
}
