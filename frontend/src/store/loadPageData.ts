import { profileService } from "../features/profile/services/profileService";
import { foodService } from "../features/catalog/services/foodService";
import { mealPlanService } from "../features/meals/services/mealPlanService";
import { activityService } from "../features/activity/services/activityService";
import type { UserData } from "../types";
import { applyResource, createUserDataCache, resourceIsFresh, resourcesForPage, type DataResource, type UserDataCache } from "./userDataCache";

const loaders = {
  profile: () => profileService.get(), foods: () => foodService.list(), plans: () => mealPlanService.list(),
  favorites: () => foodService.favorites(), disliked: async () => (await foodService.preferences()).disliked, checkIns: () => activityService.getCheckIns(),
  checkedShopping: () => activityService.getShopping(), capabilities: () => mealPlanService.capabilities(),
};
async function readResource<K extends DataResource>(cache: UserDataCache, key: K, force: boolean): Promise<UserData[K]> {
  if (!force && resourceIsFresh(cache, key)) return cache.data[key];
  const active = cache.pending.get(key);
  if (active && !force) return active as Promise<UserData[K]>;
  // Tools refresh after a successful write: an older in-flight GET is not that refresh.
  if (active) cache.versions.set(key, (cache.versions.get(key) ?? 0) + 1);
  const version = cache.versions.get(key) ?? 0;
  const pending = loaders[key]().then(value => {
    if (version === (cache.versions.get(key) ?? 0)) applyResource(cache, key, value as UserData[K]);
    return cache.data[key];
  }).finally(() => { if (cache.pending.get(key) === pending) cache.pending.delete(key); });
  cache.pending.set(key, pending);
  return pending;
}
export async function loadPageData(path: string, cache = createUserDataCache(null), force = false): Promise<UserData> {
  await readResource(cache, "profile", force);
  const keys = resourcesForPage(path, cache.data.profile).filter(key => key !== "profile");
  await Promise.all(keys.map(key => readResource(cache, key, force)));
  // Historical plans can reference dishes that have since left the active catalog.
  if (keys.includes("plans") && cache.loaded.has("foods")) {
    const available = new Set(cache.data.foods.map(food => food.id));
    const missing = new Set(cache.data.plans.flatMap(plan => plan.items.map(item => item.dishId)).filter(id => !available.has(id)));
    const historical = await Promise.all(Array.from(missing, id => {
      const key = `food:${id}`;
      if (!cache.pending.has(key)) {
        const pending = foodService.get(id).finally(() => cache.pending.delete(key));
        cache.pending.set(key, pending);
      }
      return cache.pending.get(key) as ReturnType<typeof foodService.get>;
    }));
    const currentIds = new Set(cache.data.foods.map(food => food.id));
    cache.data = { ...cache.data, foods: [...cache.data.foods, ...historical.filter(food => !currentIds.has(food.id))] };
  }
  return cache.data;
}
