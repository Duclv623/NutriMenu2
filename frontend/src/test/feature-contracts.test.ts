import { readSavedWorkspace } from "../store/UserDataProvider";
import { beforeEach, expect, it, vi } from "vitest";
import { toFood, foodService } from "../features/catalog/services/foodService";
import { mealPlanService } from "../features/meals/services/mealPlanService";
import { authenticate } from "../features/auth/services/authService";
vi.mock("../lib/config", () => ({ API_MODE: "api", RAG_ENABLED: true }));
beforeEach(() => { sessionStorage.clear(); vi.restoreAllMocks(); });
const dish = {
  id: 9, code: "NIN_BULK_TEST", nameVi: "Món NIN", role: "COMPOSITE_DISH", householdUnitLabel: "1 bát",
  imageUrl: "https://example.test/food.webp", standardServingCookedG: null, mealSlots: ["LUNCH"], medicalTags: [],
  recipeBreakdown: [{ ingredientId: 1, ingredientNameVi: "Nước", rawWeightG: null, sourceAmountText: "200 ml" }],
  energyKcal: 400, netCarbG: 50, proteinG: 20, fatG: 10, fiberG: null, sodiumMg: 200,
  preparationSteps: [], minServingScaledX2: 1, maxServingScaledX2: 6, stepServingScaledX2: 1,
  sourceRecordId: "source-1", solverEligible: true,
  nutritionQualityJson: JSON.stringify({ nonQuantifiedSourceAnnotations: [{ name_vi: "Nước dùng" }] }),
};
const response = (body: unknown) => new Response(JSON.stringify(body), { headers: { "Content-Type": "application/json" } });
it("keeps unknown source quantities and nutrients available to the UI", () => {
  const food = toFood(dish);
  expect(food.nutrition.fiberG).toBeNull();
  expect(food.grams).toBeNull();
  expect(food.ingredients[0].sourceAmountText).toBe("200 ml");
  expect(food.ingredientBasis).toBe("source");
  expect(food.recipeSource).toBe("NIN_BULK_TEST");
  expect(food.category).toBe("Món kết hợp");
  expect(food.role).toBe("COMPOSITE_DISH");
  expect(food.ingredients.some(row => row.name === "Nước dùng" && row.grams === null)).toBe(true);
  expect(food.ingredientsComplete).toBe(false);
  expect(toFood({ ...dish, nutritionQualityJson: "null" }).ingredientsComplete).toBe(false);
  expect(food.catalogSelectable).toBe(true);
});
it("retains historical dishes for reading while excluding them from new menus", () => {
  const food = toFood({ ...dish, code: "QA_OLD" });
  expect(food.catalogSelectable).toBe(false);
  expect(food.isHistoricalOnly).toBe(true);
});
it("removes favorites through the JWT gateway with DELETE", async () => {
  sessionStorage.setItem("nutriguide.token", "signed-token");
  const fetch = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(null, { status: 204 }));
  await foodService.favorite("9", false);
  expect(fetch).toHaveBeenCalledWith("/api/me/favorites/9", expect.objectContaining({
    method: "DELETE", headers: expect.objectContaining({ Authorization: "Bearer signed-token" }),
  }));
});
it("passes confirmed replacement version and mode without a workspace gateway", async () => {
  const fetch = vi.spyOn(globalThis, "fetch").mockResolvedValue(response({ id: "7" }));
  await mealPlanService.generate({ date: "2026-11-26", target: 1800, planningMode: "GENERAL_REFERENCE",
    snacks: true, maxMinutes: 0, excludeRepeat: false, replacePlanId: "7", baseVersion: 5, replacementConfirmed: true }, 3);
  const [url, options] = fetch.mock.calls[0];
  expect(url).toBe("/api/meal-plans/generate");
  expect(JSON.parse(options!.body as string)).toMatchObject({ planningMode: "GENERAL_REFERENCE", profileVersion: 3,
    replacePlanId: "7", baseVersion: 5, replacementConfirmed: true });
});
it("obtains CSRF, registers and logs in to store the real JWT", async () => {
  const fetch = vi.spyOn(globalThis, "fetch")
    .mockResolvedValueOnce(response({ headerName: "X-CSRF-TOKEN", token: "csrf-register" }))
    .mockResolvedValueOnce(response({ data: { id: "u1" } }))
    .mockResolvedValueOnce(response({ headerName: "X-CSRF-TOKEN", token: "csrf-login" }))
    .mockResolvedValueOnce(response({ data: { accessToken: "real-signed-jwt", user: { id: "u1", fullName: "An", email: "an@example.test" } } }));
  const user = await authenticate("An", "an@example.test", "password123", true);
  expect(user.demo).toBe(false);
  expect(sessionStorage.getItem("nutriguide.token")).toBe("real-signed-jwt");
  expect(fetch.mock.calls.map(call => call[0])).toEqual(["/api/auth/csrf", "/api/auth/register", "/api/auth/csrf", "/api/auth/login"]);
  expect(fetch.mock.calls[3][1]?.headers).toMatchObject({ "X-CSRF-TOKEN": "csrf-login" });
});

it("sends weekly requests to the personal JWT endpoint with no replacement intent", async () => {
  const fetch = vi.spyOn(globalThis, "fetch").mockResolvedValue(response([]));
  await mealPlanService.generateWeekly({ date: "2026-11-26", target: 1800, planningMode: "GENERAL_REFERENCE",
    snacks: true, maxMinutes: 0, excludeRepeat: false },3);
  expect(fetch.mock.calls[0][0]).toBe("/api/meal-plans/generate-weekly");
  expect(JSON.parse(fetch.mock.calls[0][1]!.body as string)).toMatchObject({ profileVersion: 3, replacementConfirmed: false });
});

it("loads pre-rename browser data and keeps existing null nutrition", () => {
  const workspace = readSavedWorkspace(JSON.stringify({
    profile: { activity: "light" },
    foods: [{ id: "9", nutrition: { energy: 400, carbs: 50, fiber: null } }],
    plans: [{ items: [{ id: "1", foodId: "9", servings: 1.5, nutrition: { fiber: 4, fiberG: null } }] }],
    conversations: [{ id: "older", messages: [{ text: "Bữa trưa có món gì?", mealResult: { context: { plan_id: "7" } } }] }],
  }));
  expect(workspace.profile.activityLevel).toBe("light");
  expect(workspace.foods[0].nutrition).toEqual({ energyKcal: 400, netCarbG: 50, fiberG: null });
  expect(workspace.plans[0].items[0]).toMatchObject({ dishId: "9", servingMultiplier: 1.5, nutrition: { fiberG: null } });
  expect(workspace.conversations[0].messages[0].mealResult?.context?.plan_id).toBe("7");
});
