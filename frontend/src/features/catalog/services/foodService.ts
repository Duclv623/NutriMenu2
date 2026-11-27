import { request } from "../../../lib/api";
import type { Food, MealType } from "../../../types";
import { foodCategoryLabel } from "../utils/foodLabels";

type Dish = {
  giLevel?: Food["giLevel"];
  code: string; sourceRecordId?: string; sourceUrl?: string; nutritionBasis?: string;
  nutritionQualityJson?: string; solverEligible?: boolean; servingWeightBasis?: string;
  id: number; nameVi: string; role: string; householdUnitLabel: string; imageUrl?: string;
  standardServingCookedG: number | null; mealSlots: string[]; medicalTags: string[];
  recipeBreakdown: { ingredientId: number; ingredientNameVi: string; rawWeightG: number | null; sourceAmountText?: string }[];
  energyKcal: number | null; netCarbG: number | null; proteinG: number | null; fatG: number | null; fiberG: number | null; sodiumMg: number | null;
  preparationSteps: string[]; preparationGuideKind?: string;
  minServingScaledX2: number; maxServingScaledX2: number; stepServingScaledX2: number;
};

const slots: Record<string, MealType> = {
  BREAKFAST: "breakfast", LUNCH: "lunch", DINNER: "dinner", AFTERNOON_SNACK: "snack", MORNING_SNACK: "snack",
};

export function toFood(dish: Dish): Food {
  let quality: { nonQuantifiedSourceAnnotations?: { name_vi?: string; nameSourceText?: string }[]; blockingOcrUncertainties?: unknown[]; carbohydrateDefinition?: string } = {};
  let qualityKnown = true;
  try {
    const parsed = JSON.parse(dish.nutritionQualityJson || "{}");
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error("Invalid source quality");
    quality = parsed;
  } catch { qualityKnown = false; }
  const ingredients = dish.recipeBreakdown.map(row => ({
    id: String(row.ingredientId), name: row.ingredientNameVi, grams: row.rawWeightG ?? null, sourceAmountText: row.sourceAmountText,
  }));
  const annotations = Array.isArray(quality.nonQuantifiedSourceAnnotations) ? quality.nonQuantifiedSourceAnnotations : [];
  const sourceNotes = annotations.map((note, index) => ({
    id: `${dish.id}-source-note-${index}`, name: note.name_vi || note.nameSourceText || "",
    grams: null, sourceAmountText: "Nguồn chưa ghi định lượng riêng",
  })).filter(note => note.name && !ingredients.some(row => row.name.trim().toLowerCase() === note.name.trim().toLowerCase()));
  ingredients.push(...sourceNotes);
  const nutrition = {
    energyKcal: dish.energyKcal, netCarbG: dish.netCarbG, proteinG: dish.proteinG, fatG: dish.fatG,
    fiberG: dish.fiberG, sodiumMg: dish.sodiumMg,
  };
  if (!Object.values(nutrition).every(value => value === null || Number.isFinite(value))) throw new Error("Món ăn có số liệu dinh dưỡng không hợp lệ.");
  return {
    id: String(dish.id), name: dish.nameVi, category: foodCategoryLabel(dish.role), role: dish.role,
    description: dish.householdUnitLabel, image: dish.imageUrl || "", emoji: "🥗", color: "#81b29a",
    giLevel: dish.giLevel, grams: dish.standardServingCookedG, minutes: null, meals: dish.mealSlots.map(s => slots[s]).filter(Boolean),
    allergens: dish.medicalTags.filter(t => t.startsWith("ALLERGEN_")), ingredients, nutrition,
    steps: dish.preparationSteps, preparationGuideKind: dish.preparationGuideKind,
    ingredientsComplete: ingredients.length > 0 && qualityKnown && !sourceNotes.length && !(quality.blockingOcrUncertainties || []).length,
    ingredientBasis: dish.sourceRecordId || dish.code.startsWith("NIN_BULK_") ? "source" : "raw",
    recipeSource: dish.code, catalogSelectable: dish.code.startsWith("NIN_BULK_"), isHistoricalOnly: !dish.code.startsWith("NIN_BULK_"),
    sourceRecordId: dish.sourceRecordId, sourceUrl: dish.sourceUrl, nutritionBasis: dish.nutritionBasis,
    carbohydrateDefinition: quality.carbohydrateDefinition || (dish.code.startsWith("NIN_") ? "UNSPECIFIED_SOURCE_GLUCID" : null),
    nutritionQualityJson: dish.nutritionQualityJson, solverEligible: dish.solverEligible,
    servingWeightBasis: dish.servingWeightBasis, householdUnitLabel: dish.householdUnitLabel,
    servingRules: { min: (dish.minServingScaledX2 ?? 1) / 2, max: (dish.maxServingScaledX2 ?? 6) / 2,
      step: (dish.stepServingScaledX2 ?? 1) / 2 },
  };
}

export const foodService = {
  list: async () => (await request<Dish[]>("/foods")).map(toFood),
  get: async (id: string) => toFood(await request<Dish>('/foods/' + encodeURIComponent(id))),
  preferences: () => request<{ favorites: string[]; disliked: string[] }>("/foods/preferences"),
  favorites: () => request<string[]>("/me/favorites"),
  removeDislike: (id: string) => request<void>(`/foods/tools/users/me/dishes/${encodeURIComponent(id)}/dislike`, { method: "DELETE" }),
  favorite: (id: string, selected: boolean) =>
    request<void>(`/me/favorites/${encodeURIComponent(id)}`, {
      method: selected ? "PUT" : "DELETE",
    }),
};
