import { describe, expect, it } from "vitest";
import { createDemoData } from "../data/seed";
import {
  dailySummaries,
  dashboardInsights,
  recordedAverage,
} from "../insights";
import { shoppingDetails, shoppingList, sumNutrition } from "../utils";

const today = "2026-11-26";
function state() {
  const w = structuredClone(createDemoData());
  w.plans = [
    { ...w.plans[0], date: today, dataMode: "api", solverStatus: "FEASIBLE" },
  ];
  w.completedMeals = [];
  return w;
}
describe("nutrition journal and insights", () => {
  it("keeps an unrecorded day unknown rather than counting it as zero", () => {
    const w = state();
    const days = dailySummaries(w, today);
    expect(days).toHaveLength(7);
    expect(days[0].planned).toBeNull();
    expect(days.at(-1)!.recorded).toBeNull();
    expect(recordedAverage(days)).toBeNull();
    const insights = dashboardInsights(w, days, today);
    expect(insights.some((i) => i.id === "no-records")).toBe(true);
    expect(insights.some((i) => i.id === "above-target")).toBe(false);
  });
  it("uses item snapshots once, excludes unconfirmed dishes and does not dilute averages with missing days", () => {
    const w = state();
    const plan = w.plans[0];
    const item = plan.items[0];
    item.servingMultiplier = 3;
    item.nutrition = {
      energyKcal: 400,
      netCarbG: 40,
      proteinG: 20,
      fatG: 15,
      fiberG: null,
      sodiumMg: 200,
    };
    w.completedMeals = [`${plan.id}:${item.id}`, `${plan.id}:deleted-item`];
    const days = dailySummaries(w, today);
    expect(days.at(-1)!.recorded?.energyKcal).toBe(400);
    expect(days.at(-1)!.recorded?.fiberG).toBeNull();
    expect(days.at(-1)!.recordedCount).toBe(1);
    expect(recordedAverage(days)).toBe(400);
    expect(
      dashboardInsights(w, days, today).some((i) => i.id === "partial"),
    ).toBe(true);
  });
  it("does not count revisions or future plans twice", () => {
    const w = state();
    const old = w.plans[0];
    const next = {
      ...old,
      id: "new",
      version: old.version + 1,
      createdAt: "2099-01-01",
      target: 1900,
    };
    w.plans.push(next, { ...old, id: "future", date: "2026-11-27" });
    w.completedMeals = [`${old.id}:${old.items[0].id}`];
    const days = dailySummaries(w, today);
    expect(days.at(-1)!.plan?.id).toBe("new");
    expect(days.at(-1)!.target).toBe(1900);
    expect(days.at(-1)!.recordedCount).toBe(0);
    expect(days.some((day) => day.date > today)).toBe(false);
  });
  it("warns about recorded excess while preserving honest incomplete-log wording", () => {
    const w = state();
    w.plans[0].target = 100;
    w.completedMeals = [`${w.plans[0].id}:${w.plans[0].items[0].id}`];
    const insights = dashboardInsights(w, dailySummaries(w, today), today);
    expect(insights.some((i) => i.id === "above-target")).toBe(true);
    expect(insights.some((i) => i.id === "all-recorded")).toBe(false);
    w.profile.version += 1;
    expect(
      dashboardInsights(w, dailySummaries(w, today), today).some(
        (i) => i.id === "stale",
      ),
    ).toBe(true);
  });
  it("counts repeated food by distinct recorded days, not planned portions", () => {
    const w = state();
    const item = w.plans[0].items[0];
    w.plans = ["2026-11-24", "2026-11-25", today].map((date, index) => ({
      ...w.plans[0],
      id: String(index),
      date,
      items: [item],
    }));
    expect(
      dashboardInsights(w, dailySummaries(w, today), today).some(
        (i) => i.id === "repeat",
      ),
    ).toBe(false);
    w.completedMeals = w.plans.map((plan) => `${plan.id}:${item.id}`);
    expect(
      dashboardInsights(w, dailySummaries(w, today), today).find(
        (i) => i.id === "repeat",
      )?.description,
    ).toContain("3 ngày");
  });
  it("can use a saved nutrient snapshot after a catalog entry is unavailable", () => {
    const w = state();
    const item = w.plans[0].items[0];
    item.nutrition = {
      energyKcal: 450,
      netCarbG: 50,
      proteinG: 20,
      fatG: 15,
      fiberG: null,
      sodiumMg: null,
    };
    expect(sumNutrition([item], []).energyKcal).toBe(450);
  });
});

describe("usable shopping quantities", () => {
  it("does not block a known recipe merely because an unrelated nutrient is missing", () => {
    const w = state();
    const item = w.plans[0].items[0];
    w.plans[0].items = [item];
    const food = w.foods.find((food) => food.id === item.dishId)!;
    food.nutrition.sodiumMg = null;
    expect(shoppingList(w.plans[0], w.foods).length).toBeGreaterThan(0);
  });
  it("retains source units and known quantities while flagging missing recipe data", () => {
    const w = state();
    const item = w.plans[0].items[0];
    w.plans[0].items = [{ ...item, servingMultiplier: 2 }];
    const food = w.foods.find((food) => food.id === item.dishId)!;
    food.ingredientsComplete = false;
    food.ingredients = [
      { id: "a", name: "Rau", grams: 100 },
      { id: "b", name: "Nước", grams: null, sourceAmountText: "200 ml" },
      { id: "c", name: "Muối", grams: null },
    ];
    const result = shoppingDetails(w.plans[0], w.foods);
    expect(result.ingredients.find((i) => i.id === "a")?.grams).toBe(200);
    expect(result.ingredients.find((i) => i.id === "b")?.grams).toBeNull();
    expect(result.ingredients.find((i) => i.id === "b")?.details[0]).toContain(
      "200 ml × 2",
    );
    expect(result.incompleteFoods).toContain(food.name);
    expect(() => shoppingList(w.plans[0], w.foods)).toThrow();
  });
});
