import { request } from "../../../lib/api";
import type { Food, MealType } from "../../../types";

type Dish = {
  giLevel?: Food["giLevel"];
  id: number; nameVi: string; role: string; householdUnitLabel: string; imageUrl?: string;
  standardServingCookedG: number; mealSlots: string[]; medicalTags: string[];
  recipeBreakdown: { ingredientId: number; ingredientNameVi: string; rawWeightG: number }[];
  caloriesKcal: number; netCarbG: number; proteinG: number; fatG: number; fiberG: number; sodiumMg: number;
  preparationSteps: string[]; preparationGuideKind?: string;
  minServingScaledX2: number; maxServingScaledX2: number; stepServingScaledX2: number;
};

const slots: Record<string, MealType> = {
  BREAKFAST: "breakfast", LUNCH: "lunch", DINNER: "dinner", AFTERNOON_SNACK: "snack", MORNING_SNACK: "snack",
};

function toFood(dish: Dish): Food {
  const ingredients = dish.recipeBreakdown.map(row => ({
    id: String(row.ingredientId), name: row.ingredientNameVi, grams: row.rawWeightG,
  }));
  const nutrition = {
    energy: dish.caloriesKcal, carbs: dish.netCarbG, protein: dish.proteinG, fat: dish.fatG,
    fiber: dish.fiberG, sodium: dish.sodiumMg,
  };
  if (!Object.values(nutrition).every(Number.isFinite)) throw new Error("Món ăn thiếu dữ liệu dinh dưỡng.");
  return {
    id: String(dish.id), name: dish.nameVi, category: dish.role, role: dish.role,
    description: dish.householdUnitLabel, image: dish.imageUrl || "", emoji: "🥗", color: "#81b29a",
    giLevel: dish.giLevel, grams: dish.standardServingCookedG, minutes: null, meals: dish.mealSlots.map(s => slots[s]).filter(Boolean),
    allergens: dish.medicalTags.filter(t => t.startsWith("ALLERGEN_")), ingredients, nutrition,
    steps: dish.preparationSteps, preparationGuideKind: dish.preparationGuideKind,
    ingredientsComplete: ingredients.length > 0, ingredientBasis: "raw",
    servingRules: { min: (dish.minServingScaledX2 ?? 1) / 2, max: (dish.maxServingScaledX2 ?? 6) / 2,
      step: (dish.stepServingScaledX2 ?? 1) / 2 },
  };
}

export const foodService = {
  list: async () => (await request<Dish[]>("/foods")).map(toFood),
  get: async (id: string) => toFood(await request<Dish>('/foods/' + encodeURIComponent(id))),
  favorites: () => request<string[]>("/me/favorites"),
  favorite: (id: string, selected: boolean) =>
    request<void>(`/me/favorites/${encodeURIComponent(id)}`, {
      method: selected ? "PUT" : "DELETE",
    }),
};
