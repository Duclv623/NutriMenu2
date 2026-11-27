import type { Profile, UserData } from "../types";

export const DATA_RESOURCES = ["profile", "foods", "plans", "favorites", "disliked", "checkIns", "checkedShopping", "capabilities"] as const;
export type DataResource = typeof DATA_RESOURCES[number];
export type UserDataCache = {
  owner: string | null;
  data: UserData;
  loaded: Set<DataResource>;
  updatedAt: Map<DataResource, number>;
  versions: Map<DataResource, number>;
  pending: Map<string, Promise<unknown>>;
};
export const emptyUserData = (): UserData => ({
  profile: { name: "", dateOfBirth: "", age: 0, gender: "", height: 0, weight: 0, targetWeight: 0,
    activityLevel: "", goal: "", allergies: [], excludedFoods: [], preferences: "", pregnant: false,
    specialDiet: false, confirmed: false, version: 0 },
  foods: [], plans: [], favorites: [], disliked: [], checkedShopping: [], checkIns: [], conversations: [], completedMeals: [],
});
export function createUserDataCache(owner: string | null, data = emptyUserData()): UserDataCache {
  return { owner, data, loaded: new Set(), updatedAt: new Map(), versions: new Map(), pending: new Map() };
}
export function resourcesForPage(path: string, profile?: Profile): DataResource[] {
  const dashboard = path === "/" && !!profile?.dateOfBirth;
  const meals = dashboard || ["/thuc-don", "/tro-ly", "/mua-sam", "/lich-su"].includes(path);
  const catalog = meals || ["/mon-an", "/yeu-thich", "/quan-tri"].includes(path);
  const keys: DataResource[] = ["profile"];
  if (catalog) keys.push("foods", "favorites", "disliked");
  if (meals) keys.push("plans", "capabilities");
  if (dashboard || path === "/theo-doi") keys.push("checkIns");
  if (path === "/mua-sam") keys.push("checkedShopping");
  return keys;
}
export function isPageDataReady(path: string, cache: UserDataCache) {
  const keys = resourcesForPage(path, cache.data.profile);
  if (!keys.every(key => cache.loaded.has(key))) return false;
  if (keys.includes("plans")) {
    const available = new Set(cache.data.foods.map(food => food.id));
    return cache.data.plans.every(plan => plan.items.every(item => available.has(item.dishId)));
  }
  return true;
}
export function resourceIsFresh(cache: UserDataCache, key: DataResource) {
  const maxAge = key === "foods" ? 5 * 60_000 : 30_000;
  return cache.loaded.has(key) && Date.now() - (cache.updatedAt.get(key) ?? 0) < maxAge;
}
export function applyResource<K extends DataResource>(cache: UserDataCache, key: K, value: UserData[K]) {
  if (key === "foods") {
    const foods = value as UserData["foods"];
    const incoming = new Set(foods.map(food => food.id));
    const referenced = new Set(cache.data.plans.flatMap(plan => plan.items.map(item => item.dishId)));
    const historical = cache.data.foods.filter(food => referenced.has(food.id) && !incoming.has(food.id))
      .map(food => ({ ...food,catalogSelectable: false,isHistoricalOnly: true }));
    value = [...foods, ...historical] as UserData[K];
  }
  cache.data = { ...cache.data, [key]: value };
  if (key === "plans") {
    const plans = value as UserData["plans"];
    cache.data.completedMeals = plans.flatMap(plan => plan.items.filter(item => item.completed).map(item => `${plan.id}:${item.id}`));
  }
  cache.loaded.add(key);
  cache.updatedAt.set(key, Date.now());
}
// A successful local/server mutation must win over any older read still in flight.
export function updateCachedUserData(cache: UserDataCache, next: UserData) {
  for (const key of DATA_RESOURCES) {
    if (next[key] !== cache.data[key] || (key === "plans" && next.completedMeals !== cache.data.completedMeals)) {
      cache.versions.set(key, (cache.versions.get(key) ?? 0) + 1);
      cache.loaded.add(key);
      cache.updatedAt.set(key, Date.now());
    }
  }
  cache.data = next;
}
