import { beforeEach, afterEach, expect, it, vi } from "vitest";
import { createDemoData } from "../data/seed";
import { loadPageData } from "../store/loadPageData";
import { createUserDataCache, isPageDataReady, updateCachedUserData } from "../store/userDataCache";
const api = vi.hoisted(() => ({ profile: vi.fn(), foods: vi.fn(), plans: vi.fn(), preferences: vi.fn(), favorites: vi.fn(), checkIns: vi.fn(), shopping: vi.fn(), capabilities: vi.fn(), food: vi.fn() }));
vi.mock("../features/profile/services/profileService", () => ({ profileService: { get: api.profile } }));
vi.mock("../features/catalog/services/foodService", () => ({ foodService: { list: api.foods, preferences: api.preferences, favorites: api.favorites, get: api.food } }));
vi.mock("../features/meals/services/mealPlanService", () => ({ mealPlanService: { list: api.plans, capabilities: api.capabilities } }));
vi.mock("../features/activity/services/activityService", () => ({ activityService: { getCheckIns: api.checkIns, getShopping: api.shopping } }));
const data = createDemoData();
beforeEach(() => {
  vi.clearAllMocks();
  api.preferences.mockResolvedValue({ favorites: [], disliked: [] });
  api.profile.mockResolvedValue(data.profile); api.foods.mockResolvedValue(data.foods); api.plans.mockResolvedValue(data.plans);
  api.favorites.mockResolvedValue(data.favorites); api.checkIns.mockResolvedValue(data.checkIns);
  api.shopping.mockResolvedValue([]); api.capabilities.mockResolvedValue({ generate: true });
});
afterEach(() => vi.restoreAllMocks());
it("reuses profile and the entire catalog across pages and deduplicates concurrent requests", async () => {
  const cache = createUserDataCache("a");
  await loadPageData("/mon-an",cache);
  await Promise.all([loadPageData("/thuc-don",cache),loadPageData("/mua-sam",cache),loadPageData("/theo-doi",cache)]);
  for (const name of ["profile","foods","plans","favorites","checkIns","shopping","capabilities"] as const)
    expect(api[name],name).toHaveBeenCalledTimes(1);
  expect(isPageDataReady("/thuc-don",cache)).toBe(true);
  expect(cache.data.checkIns).toEqual(data.checkIns);
  expect(cache.data.plans).toEqual(data.plans);
});
it("keeps a removed favorite when navigating back without fetching an older list", async () => {
  const cache = createUserDataCache("a");
  await loadPageData("/mon-an",cache);
  updateCachedUserData(cache,{ ...cache.data,favorites: [] });
  await loadPageData("/yeu-thich",cache);
  expect(cache.data.favorites).toEqual([]);
  expect(api.favorites).toHaveBeenCalledTimes(1);
});
it("an older plan read cannot overwrite a newly saved or completed plan", async () => {
  const cache = createUserDataCache("a");
  await loadPageData("/mon-an",cache);
  let finish!: (plans: typeof data.plans) => void;
  api.plans.mockReturnValueOnce(new Promise(resolve => { finish = resolve; }));
  const pending = loadPageData("/thuc-don",cache);
  await vi.waitFor(() => expect(api.plans).toHaveBeenCalledTimes(1));
  const saved = { ...data.plans[0],id: "new-plan",version: 20 };
  updateCachedUserData(cache,{ ...cache.data,plans: [saved],completedMeals: ["new-plan:item"] });
  finish([]);
  await pending;
  expect(cache.data.plans[0].id).toBe("new-plan");
  expect(cache.data.completedMeals).toEqual(["new-plan:item"]);
});
it("revalidates stale personal data while keeping a fresh catalog and a renderable page", async () => {
  const clock = vi.spyOn(Date,"now").mockReturnValue(100_000);
  const cache = createUserDataCache("a");
  await loadPageData("/mon-an",cache);
  clock.mockReturnValue(131_000);
  expect(isPageDataReady("/mon-an",cache)).toBe(true);
  await loadPageData("/mon-an",cache);
  expect(api.profile).toHaveBeenCalledTimes(2);
  expect(api.favorites).toHaveBeenCalledTimes(2);
  expect(api.foods).toHaveBeenCalledTimes(1);
});
it("an explicit reload fetches fresh data and restores the source snapshot", async () => {
  const cache = createUserDataCache("a");
  await loadPageData("/mon-an",cache);
  await loadPageData("/mon-an",cache,true);
  expect(api.foods).toHaveBeenCalledTimes(2);
  expect(api.profile).toHaveBeenCalledTimes(2);
});
it("a tool refresh starts a new plan read after saving and ignores an earlier in-flight read", async () => {
  const cache = createUserDataCache("a");
  await loadPageData("/mon-an", cache);
  let finish!: (plans: typeof data.plans) => void;
  api.plans.mockReturnValueOnce(new Promise(resolve => { finish = resolve; }));
  const oldRead = loadPageData("/thuc-don", cache);
  await vi.waitFor(() => expect(api.plans).toHaveBeenCalledTimes(1));
  const saved = { ...data.plans[0], id: "saved-by-tool", version: 20 };
  api.plans.mockResolvedValueOnce([saved]);
  await loadPageData("/thuc-don", cache, true);
  expect(cache.data.plans[0].id).toBe("saved-by-tool");
  finish([]);
  await oldRead;
  expect(cache.data.plans[0].id).toBe("saved-by-tool");
});
it("a catalog refresh keeps dishes needed by historical plans without making them selectable", async () => {
  const cache = createUserDataCache("a");
  await loadPageData("/thuc-don",cache);
  const referenced = data.plans[0].items[0].dishId;
  api.foods.mockResolvedValueOnce(data.foods.filter(food => food.id !== referenced));
  await loadPageData("/mon-an",cache,true);
  expect(cache.data.foods.find(food => food.id === referenced)).toMatchObject({ isHistoricalOnly: true,catalogSelectable: false });
  expect(isPageDataReady("/thuc-don",cache)).toBe(true);
});
it("separate account caches do not share favorites, plans or in-flight requests", async () => {
  const first = createUserDataCache("a"); const second = createUserDataCache("b");
  api.favorites.mockResolvedValueOnce(["private-a"]).mockResolvedValueOnce(["private-b"]);
  await loadPageData("/mon-an",first); await loadPageData("/mon-an",second);
  expect(first.data.favorites).toEqual(["private-a"]);
  expect(second.data.favorites).toEqual(["private-b"]);
  expect(api.foods).toHaveBeenCalledTimes(2);
});
