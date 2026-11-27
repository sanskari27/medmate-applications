import { describe, expect, it } from "vitest";
import { sectionProblem, toFormData, type SectionDraft } from "./model";

function draft(overrides: Partial<SectionDraft> = {}): SectionDraft {
  return {
    category: "Pain",
    position: "1",
    image: new File([new Uint8Array([1])], "pain.png", { type: "image/png" }),
    ...overrides,
  };
}

describe("section drafts", () => {
  it("requires a category and an image when creating", () => {
    expect(sectionProblem(draft({ category: "  " }), true)).toBe("Category is required");
    expect(sectionProblem(draft({ image: null }), true)).toBe("Image is required");
    expect(sectionProblem(draft({ image: null }), false)).toBeNull();
    expect(sectionProblem(draft({ position: "-1" }), true)).toBe("Position is not valid");
  });

  it("packs the section json and image into form data", async () => {
    const body = toFormData(draft());
    const section = body.get("section");
    expect(section).toBeInstanceOf(Blob);
    expect(JSON.parse(await (section as Blob).text())).toEqual({ category: "Pain", position: 1 });
    expect(body.get("image")).toBeInstanceOf(File);
    expect(toFormData(draft({ image: null })).get("image")).toBeNull();
  });
});
