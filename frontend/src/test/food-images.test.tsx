import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { FoodMark } from "../components/common/ui";
import { ninImages } from "../data/ninImages";
import { createDemoData } from "../data/seed";
import { foodImageSources } from "../foodImages";

const code = "NIN_REAL_VPF_000398";
const image = ninImages[code];
const food = {
  ...createDemoData().foods[0],
  recipeSource: code,
  image: image.originalApiImageUrl,
};

describe("original NIN source photos", () => {
  it("resolves a matching runtime code and original API URL to the saved photo", () => {
    expect(Object.keys(ninImages).filter((key) => key.startsWith("NIN_REAL_"))).toHaveLength(40);
    expect(foodImageSources(food)).toEqual([
      image.localAssetPath,
      image.originalApiImageUrl,
    ]);
    render(<FoodMark food={food} />);
    expect(screen.getByRole("img")).toHaveAttribute(
      "src",
      image.localAssetPath,
    );
  });

  it("uses the downloaded bulk photo for a new catalog dish without matching by name", () => {
    const bulkCode = "NIN_BULK_HAN_111001";
    const bulkImage = ninImages[bulkCode];
    expect(bulkImage).toBeDefined();
    const bulkFood = { ...food, recipeSource: bulkCode, image: bulkImage.originalApiImageUrl };
    expect(foodImageSources(bulkFood)[0]).toMatch(/^\/food-images\/nin-bulk-20261126\//);
    render(<FoodMark food={bulkFood} />);
    expect(screen.getByRole("img")).toHaveAttribute("src", bulkImage.localAssetPath);
  });

  it("preserves a custom API image when the known code's URL changes", () => {
    const custom = "https://example.com/custom-photo.webp";
    expect(foodImageSources({ ...food, image: custom })).toEqual([custom]);
  });

  it("does not map QA foods or unknown source keys by matching name or URL", () => {
    for (const recipeSource of [undefined, "QA", "NIN_REAL_VPF_999999"])
      expect(foodImageSources({ ...food, recipeSource })).toEqual([
        image.originalApiImageUrl,
      ]);
  });

  it("falls back to the original API photo, then the existing icon, and resets for a new photo", () => {
    const { rerender } = render(<FoodMark food={food} />);
    fireEvent.error(screen.getByRole("img"));
    expect(screen.getByRole("img")).toHaveAttribute(
      "src",
      image.originalApiImageUrl,
    );
    fireEvent.error(screen.getByRole("img"));
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(document.querySelector(".food-mark svg")).toBeInTheDocument();
    rerender(
      <FoodMark
        food={{ ...food, image: "https://example.com/replacement.webp" }}
      />,
    );
    expect(screen.getByRole("img")).toHaveAttribute(
      "src",
      "https://example.com/replacement.webp",
    );
  });
});
