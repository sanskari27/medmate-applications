import { describe, expect, it } from "vitest";
import { sectionProblem, toFormData, type SectionDraft } from "./model";

function draft(overrides: Partial<SectionDraft> = {}): SectionDraft {
  return {
    title: "Everyday care",
    position: "1",
    image: new File([new Uint8Array([1])], "section.png", { type: "image/png" }),
    banner: new File([new Uint8Array([2])], "banner.png", { type: "image/png" }),
    categories: [
      {
        key: "pain",
        category: "Pain",
        images: [
          {
            key: "kept",
            id: "image-1",
            file: null,
            url: "http://127.0.0.1/pain.png",
          },
          {
            key: "new",
            id: null,
            file: new File([new Uint8Array([3])], "tile.png", { type: "image/png" }),
            url: null,
          },
        ],
      },
    ],
    ...overrides,
  };
}

describe("section drafts", () => {
  it("requires a name, section media, and a category image", () => {
    expect(sectionProblem(draft({ title: "  " }), true)).toBe("Name is required");
    expect(sectionProblem(draft({ image: null }), true)).toBe("Image is required");
    expect(sectionProblem(draft({ banner: null }), true)).toBe("Banner is required");
    expect(sectionProblem(draft({ image: null, banner: null }), false)).toBeNull();
    expect(sectionProblem(draft({ position: "-1" }), true)).toBe("Position is not valid");
    expect(sectionProblem(draft({ categories: [] }), true)).toBe("Add a category");
    expect(
      sectionProblem(
        draft({
          categories: [{ key: "pain", category: "Pain", images: [] }],
        }),
        true,
      ),
    ).toBe("Each category needs an image");
  });

  it("packs the section json, banner, and category images into form data", async () => {
    const body = toFormData(draft());
    const section = body.get("section");
    expect(section).toBeInstanceOf(Blob);
    expect(JSON.parse(await (section as Blob).text())).toEqual({
      title: "Everyday care",
      position: 1,
      categories: [
        {
          category: "Pain",
          images: [{ id: "image-1" }, { file: true }],
        },
      ],
    });
    expect(body.get("image")).toBeInstanceOf(File);
    expect(body.get("banner")).toBeInstanceOf(File);
    expect(body.getAll("categoryImage")).toHaveLength(1);
    expect(toFormData(draft({ image: null, banner: null })).get("banner")).toBeNull();
  });
});
