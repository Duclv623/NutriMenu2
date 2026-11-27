import { beforeEach, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { Link, MemoryRouter, useLocation } from "react-router-dom";
import { createDemoData } from "../data/seed";
import { UserDataProvider, useApp } from "../store/UserDataProvider";
const api = vi.hoisted(() => ({ profile: vi.fn(), foods: vi.fn(), plans: vi.fn(), preferences: vi.fn(), favorites: vi.fn(), checkIns: vi.fn(), shopping: vi.fn(), capabilities: vi.fn() }));
vi.mock("../lib/config", () => ({ API_MODE: "api", RAG_ENABLED: true }));
vi.mock("../features/profile/services/profileService", () => ({ profileService: { get: api.profile } }));
vi.mock("../features/catalog/services/foodService", () => ({ foodService: { list: api.foods, preferences: api.preferences, favorites: api.favorites } }));
vi.mock("../features/meals/services/mealPlanService", () => ({ mealPlanService: { list: api.plans, capabilities: api.capabilities } }));
vi.mock("../features/activity/services/activityService", () => ({ activityService: { getCheckIns: api.checkIns, getShopping: api.shopping } }));
const data = createDemoData();
beforeEach(() => {
  vi.clearAllMocks();
  api.preferences.mockResolvedValue({ favorites: [], disliked: [] });
  sessionStorage.setItem("nutriguide.user",JSON.stringify({ id: "a",name: "An",email: "a@test.invalid",demo: false }));
  api.profile.mockResolvedValue({ ...data.profile,name: "Account A",dateOfBirth: "1990-01-01" });
  api.foods.mockResolvedValue(data.foods); api.plans.mockResolvedValue(data.plans); api.favorites.mockResolvedValue(["private-a"]);
  api.checkIns.mockResolvedValue([]); api.shopping.mockResolvedValue([]); api.capabilities.mockResolvedValue({ generate: true });
});
let previousUpdate: ReturnType<typeof useApp>["update"];
function Probe() {
  const ctx = useApp(); const { pathname } = useLocation();
  return <>
    <Link to="/">dashboard</Link><Link to="/mon-an">catalog</Link><Link to="/thuc-don">meals</Link><Link to="/ho-so">profile</Link>
    <output data-testid="state">{ctx.loading ? "loading" : `ready:${pathname}`}</output>
    <output data-testid="name">{ctx.userData.profile.name}</output>
    <output data-testid="favorites">{ctx.userData.favorites.join(",")}</output>
    <output data-testid="error">{ctx.loadError}</output>
    <button onClick={() => void ctx.reload()}>reload</button>
    <button onClick={() => { previousUpdate = ctx.update; ctx.update(s => ({ ...s,favorites: [] })); }}>remove</button>
    <button onClick={() => ctx.login({ id: "b",name: "B",email: "b@test.invalid",demo: false })}>switch</button>
  </>;
}
function mount(path = "/") {
  return render(<MemoryRouter initialEntries={[path]}><UserDataProvider><Probe /></UserDataProvider></MemoryRouter>);
}
it("shows already loaded pages immediately without a full-screen loading state or repeated catalog calls", async () => {
  mount();
  await screen.findByText("ready:/");
  fireEvent.click(screen.getByText("catalog"));
  expect(screen.getByTestId("state")).toHaveTextContent("ready:/mon-an");
  fireEvent.click(screen.getByText("meals"));
  expect(screen.getByTestId("state")).toHaveTextContent("ready:/thuc-don");
  fireEvent.click(screen.getByText("dashboard"));
  expect(screen.getByTestId("state")).toHaveTextContent("ready:/");
  expect(api.profile).toHaveBeenCalledTimes(1);
  expect(api.foods).toHaveBeenCalledTimes(1);
  expect(api.plans).toHaveBeenCalledTimes(1);
});
it("keeps a mutation after navigating and blocks callbacks from the previous account", async () => {
  mount(); await screen.findByText("ready:/");
  fireEvent.click(screen.getByText("remove"));
  fireEvent.click(screen.getByText("catalog"));
  expect(screen.getByTestId("favorites")).toBeEmptyDOMElement();
  api.profile.mockResolvedValue({ ...data.profile,name: "Account B",dateOfBirth: "1990-01-01" });
  api.favorites.mockResolvedValue(["private-b"]);
  fireEvent.click(screen.getByText("switch"));
  await screen.findByText("Account B");
  previousUpdate(s => ({ ...s,profile: { ...s.profile,name: "Leaked account A" },favorites: ["private-a"] }));
  expect(screen.getByTestId("name")).toHaveTextContent("Account B");
  expect(screen.getByTestId("favorites")).toHaveTextContent("private-b");
});
it("keeps the current page visible if a background refresh fails", async () => {
  mount(); await screen.findByText("ready:/");
  api.foods.mockRejectedValueOnce(new Error("Temporary catalog failure"));
  fireEvent.click(screen.getByText("reload"));
  await screen.findByText("Temporary catalog failure");
  expect(screen.getByTestId("state")).toHaveTextContent("ready:/");
  expect(screen.getByTestId("name")).toHaveTextContent("Account A");
});
it("can open a cached profile even if another service prevented the initial dashboard from loading", async () => {
  api.foods.mockRejectedValueOnce(new Error("Catalog unavailable"));
  mount();
  await screen.findByText("Catalog unavailable");
  fireEvent.click(screen.getByText("profile"));
  expect(screen.getByTestId("state")).toHaveTextContent("ready:/ho-so");
  expect(screen.getByTestId("name")).toHaveTextContent("Account A");
});
it("ignores a late response from an account that has already been switched", async () => {
  let finish!: (profile: typeof data.profile) => void;
  api.profile.mockReturnValueOnce(new Promise(resolve => { finish = resolve; }));
  api.profile.mockResolvedValue({ ...data.profile,name: "Account B",dateOfBirth: "1990-01-01" });
  mount();
  await waitFor(() => expect(api.profile).toHaveBeenCalledTimes(1));
  fireEvent.click(screen.getByText("switch"));
  await screen.findByText("Account B");
  finish({ ...data.profile,name: "Late account A",dateOfBirth: "1990-01-01" });
  await waitFor(() => expect(screen.getByTestId("name")).toHaveTextContent("Account B"));
});
