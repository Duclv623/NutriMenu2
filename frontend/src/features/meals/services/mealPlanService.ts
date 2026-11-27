import { json, request } from "../../../lib/api";
import type { GenerateInput, Plan, UserData } from "../../../types";

export const mealPlanService = {
  list: () => request<Plan[]>("/meal-plans"),
  capabilities: () => request<NonNullable<UserData["capabilities"]>>("/meal-plans/capabilities"),
  generate: (input: GenerateInput, profileVersion: number) =>
    request<Plan>("/meal-plans/generate", {
      method: "POST",
      body: json({ ...input, profileVersion }),
    }),
  generateWeekly: (input: GenerateInput, profileVersion: number) =>
    request<Plan[]>("/meal-plans/generate-weekly", {
      method: "POST",
      body: json({ ...input, profileVersion, replacementConfirmed: false }),
    }),
  previewSwap: (plan: Plan, itemId: string, dishId: string, servingMultiplier: number) =>
    request<Plan>(`/meal-plans/${encodeURIComponent(plan.id)}/swap-preview`, {
      method: "POST",
      body: json({
        baseVersion: plan.version,
        profileVersion: plan.profileVersion,
        itemId,
        dishId,
        servingMultiplier,
      }),
    }),
  swap: (
    plan: Plan,
    itemId: string,
    dishId: string,
    servingMultiplier: number,
    previewSignature?: string,
  ) =>
    request<Plan>(`/meal-plans/${encodeURIComponent(plan.id)}/swap`, {
      method: "POST",
      body: json({
        baseVersion: plan.version,
        itemId,
        dishId,
        servingMultiplier,
        previewSignature,
        profileVersion: plan.profileVersion,
      }),
    }),
  lock: (plan: Plan, itemId: string, locked: boolean) =>
    request<Plan>(
      `/meal-plans/${encodeURIComponent(plan.id)}/items/${encodeURIComponent(itemId)}/lock`,
      {
        method: "PATCH",
        body: json({ baseVersion: plan.version, locked }),
      },
    ),
  deletePlan: (id: string) =>
    request<void>(`/meal-plans/${encodeURIComponent(id)}`, {
      method: "DELETE",
    }),
  completeMeal: (key: string, completed: boolean) =>
    request<Plan>("/me/meal-logs", {
      method: "PUT",
      body: json({ key, completed }),
    }),
};
