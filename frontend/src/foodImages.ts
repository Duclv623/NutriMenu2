import { ninImages } from "./data/ninImages";
import type { Food } from "./types";
import { safeUrl } from "./utils";

export function foodImageSources(food: Food): string[] {
  const apiImage = safeUrl(food.image);
  if (!apiImage) return [];
  const source = food.recipeSource ? ninImages[food.recipeSource] : undefined;
  // Both immutable runtime identity and the original API URL must match.
  // This preserves user/API photo changes and never matches QA foods by name.
  return source && source.originalApiImageUrl === food.image
    ? [source.localAssetPath, apiImage]
    : [apiImage];
}
