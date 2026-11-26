import { profileService } from "../features/profile/services/profileService";
import { foodService } from "../features/catalog/services/foodService";
import { mealPlanService } from "../features/meals/services/mealPlanService";
import { activityService } from "../features/activity/services/activityService";
import type { UserData } from "../types";

export async function loadPageData(path: string): Promise<UserData> {
  const profile = await profileService.get();
  const needsDashboard = path === "/" && !!profile.dateOfBirth;
  const meals = needsDashboard || ["/thuc-don", "/tro-ly", "/mua-sam", "/lich-su"].includes(path);
  const catalog = meals || path === "/mon-an" || path === "/yeu-thich" || path === "/quan-tri";
  const progress = needsDashboard || path === "/theo-doi";
  const [plans, foods, favorites, checkIns, checkedShopping, capabilities] = await Promise.all([
    meals ? mealPlanService.list() : [], catalog ? foodService.list() : [],
    catalog ? foodService.favorites() : [], progress ? activityService.getCheckIns() : [],
    path === "/mua-sam" ? activityService.getShopping() : [],
    meals ? mealPlanService.capabilities() : undefined,
  ]);
  // Historical plans can reference dishes that have since left the active catalog.
  const available = new Set(foods.map(food => food.id));
  const missing = new Set(plans.flatMap(plan => plan.items.map(item => item.foodId)).filter(id => !available.has(id)));
  const historical = await Promise.all(Array.from(missing, id => foodService.get(id)));
  const completedMeals = plans.flatMap(plan => plan.items.filter(i => i.completed).map(i => plan.id + ":" + i.id));
  return { profile, plans, foods: [...foods, ...historical], favorites, checkIns, checkedShopping,
    completedMeals, capabilities, conversations: [] };
}
