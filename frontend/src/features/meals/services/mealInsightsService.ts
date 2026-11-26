import { request } from "../../../lib/api";

export type Shopping = {
  totalMealsIncluded: number;
  items: {
    ingredientId: number;
    ingredientNameVi: string;
    totalRawWeightG: number;
  }[];
};
export type Analytics = {
  analyzedDays: number;
  completedItemsCount: number;
  adherenceRatePct: number;
  avgDailyCaloriesKcal: number;
  avgDailyNetCarbG: number;
  avgDailyFiberG: number;
  avgDailyGl: number;
};

export const mealInsightsService = {
  shopping: (range: { fromDate: string; toDate: string }, signal: AbortSignal) =>
    request<Shopping>("/meal-plans/shopping-list?" + new URLSearchParams(range), { signal }),
  analytics: (days: string, signal: AbortSignal) =>
    request<Analytics>("/meal-plans/analytics?" + new URLSearchParams({ days }), { signal }),
  recent: (days: string) =>
    request<number[]>("/meal-plans/recent-dishes?" + new URLSearchParams({ days })),
};
