export type SectionImage = {
  contentType: string;
  url: string;
};

export type CategoryImage = {
  id: string;
  contentType: string;
  url: string;
};

export type SectionCategory = {
  id: string;
  category: string;
  position: number;
  images: CategoryImage[];
};

export type AppSection = {
  id: string;
  title: string;
  position: number;
  image: SectionImage;
  banner: SectionImage;
  categories: SectionCategory[];
};

export type AppSectionList = {
  sections: AppSection[];
};

export type DraftImage = {
  key: string;
  id: string | null;
  file: File | null;
  url: string | null;
};

export type DraftCategory = {
  key: string;
  category: string;
  images: DraftImage[];
};

export type SectionDraft = {
  title: string;
  position: string;
  image: File | null;
  banner: File | null;
  categories: DraftCategory[];
};

const CATEGORY_LIMIT = 12;
const IMAGE_LIMIT = 6;

export function blankCategory(): DraftCategory {
  return { key: crypto.randomUUID(), category: "", images: [] };
}

export function blankDraft(position: number): SectionDraft {
  return {
    title: "",
    position: String(position),
    image: null,
    banner: null,
    categories: [blankCategory()],
  };
}

export function draftFrom(section: AppSection): SectionDraft {
  return {
    title: section.title,
    position: String(section.position),
    image: null,
    banner: null,
    categories: section.categories.map((category) => ({
      key: category.id,
      category: category.category,
      images: category.images.map((image) => ({
        key: image.id,
        id: image.id,
        file: null,
        url: image.url,
      })),
    })),
  };
}

export function sectionProblem(draft: SectionDraft, creating: boolean): string | null {
  if (!draft.title.trim()) return "Name is required";
  if (creating && !draft.image) return "Image is required";
  if (creating && !draft.banner) return "Banner is required";
  const position = draft.position.trim();
  if (!/^\d+$/.test(position) || Number(position) > 10000) return "Position is not valid";
  if (draft.categories.length === 0) return "Add a category";
  if (draft.categories.length > CATEGORY_LIMIT) return "Too many categories";
  const names = new Set<string>();
  for (const category of draft.categories) {
    const name = category.category.trim().toLowerCase();
    if (!name) return "Category is required";
    if (names.has(name)) return "Category is already listed";
    names.add(name);
    if (category.images.length === 0) return "Each category needs an image";
    if (category.images.length > IMAGE_LIMIT) return "Too many category images";
  }
  return null;
}

export function toFormData(draft: SectionDraft): FormData {
  const body = new FormData();
  body.append(
    "section",
    new Blob(
      [
        JSON.stringify({
          title: draft.title.trim(),
          position: Number(draft.position.trim() || "0"),
          categories: draft.categories.map((category) => ({
            category: category.category.trim(),
            images: category.images.map((image) =>
              image.file ? { file: true } : { id: image.id },
            ),
          })),
        }),
      ],
      { type: "application/json" },
    ),
  );
  if (draft.image) body.append("image", draft.image);
  if (draft.banner) body.append("banner", draft.banner);
  for (const category of draft.categories) {
    for (const image of category.images) {
      if (image.file) body.append("categoryImage", image.file);
    }
  }
  return body;
}
