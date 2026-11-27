import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { UserDataProvider as AppProvider, useApp } from "../store/UserDataProvider";
import { DashboardPage as Dashboard } from "../features/dashboard/pages/DashboardPage";
import { FoodLibraryPage as FoodLibrary } from "../features/catalog/pages/FoodLibraryPage";
import { FoodDetail } from "../features/catalog/components/FoodDetail";
import { ShoppingPage } from "../features/shopping/pages/ShoppingPage";
import { SettingsPage } from "../features/settings/pages/SettingsPage";
import { createDemoData } from "../data/seed";
import type { UserData } from "../types";
import { localDate } from "../utils";

const api = vi.hoisted(() => ({
  workspace: vi.fn(),
  favorite: vi.fn(),
  completeMeal: vi.fn(),
  shopping: vi.fn(),
}));
vi.mock("../lib/config", () => ({ API_MODE: "api", RAG_ENABLED: true }));
vi.mock("../store/loadPageData", () => ({ loadPageData: async (_path: string, cache: import("../store/userDataCache").UserDataCache) => {
  const data = await api.workspace();
  cache.data = data;
  for (const key of ["profile", "foods", "plans", "favorites", "disliked", "checkIns", "checkedShopping", "capabilities"] as const) {
    cache.loaded.add(key); cache.updatedAt.set(key, Date.now());
  }
  return data;
} }));
vi.mock("../features/catalog/services/foodService", () => ({ foodService: api }));
vi.mock("../features/meals/services/mealPlanService", () => ({ mealPlanService: api }));
vi.mock("../features/activity/services/activityService", () => ({ activityService: api }));
let saved: UserData;
beforeEach(() => {
  vi.clearAllMocks();
  saved = structuredClone(createDemoData());
  saved.foods = saved.foods.map((food) => ({
    ...food,
    recipeSource: `NIN_BULK_TEST_${food.id}`,
    solverEligible: true,
  }));
  saved.plans = [
    {
      ...saved.plans[0],
      date: localDate(),
      dataMode: "api",
      solverStatus: "FEASIBLE",
    },
  ];
  saved.completedMeals = [];
  saved.capabilities = {
    generate: true,
    swap: true,
    favoriteRemoval: false,
    cookingTime: false,
    atomicVersions: true,
  };
  sessionStorage.setItem(
    "nutriguide.user",
    JSON.stringify({
      id: "a",
      name: "Minh Anh",
      email: "a@example.test",
      demo: false,
    }),
  );
  api.workspace.mockImplementation(async () => structuredClone(saved));
  api.favorite.mockImplementation(async (id: string, selected: boolean) => {
    saved.favorites = selected
      ? [...new Set([...saved.favorites, id])]
      : saved.favorites.filter((value) => value !== id);
  });
  api.completeMeal.mockImplementation(
    async (key: string, completed: boolean) => {
      saved.completedMeals = completed
        ? [...saved.completedMeals, key]
        : saved.completedMeals.filter((value) => value !== key);
      saved.plans[0].version += 1;
      return structuredClone(saved.plans[0]);
    },
  );
});
function mount(element: React.ReactNode) {
  return render(
    <MemoryRouter>
      <AppProvider>{element}</AppProvider>
    </MemoryRouter>,
  );
}
function PlanRevision() {
  return (
    <output data-testid="plan-revision">
      {useApp().userData.plans[0]?.version}
    </output>
  );
}

describe("usable dashboard and favourites", () => {
  it("removes a heart even with an old capability flag, persists it after remount, and can favourite again", async () => {
    const food = saved.foods.find((food) => saved.favorites.includes(food.id))!;
    const view = mount(<FoodLibrary favoritesOnly />);
    const heart = await screen.findByRole("button", {
      name: `Bỏ yêu thích ${food.name}`,
    });
    expect(heart).toBeEnabled();
    fireEvent.click(heart);
    await waitFor(() =>
      expect(
        screen.queryByRole("button", { name: `Bỏ yêu thích ${food.name}` }),
      ).not.toBeInTheDocument(),
    );
    expect(api.favorite).toHaveBeenCalledWith(food.id, false);
    view.unmount();
    mount(<FoodDetail food={food} onClose={vi.fn()} />);
    const add = await screen.findByRole("button", {
      name: `Yêu thích ${food.name}`,
    });
    fireEvent.click(add);
    await screen.findByRole("button", { name: `Bỏ yêu thích ${food.name}` });
    expect(api.favorite).toHaveBeenLastCalledWith(food.id, true);
  });
  it("keeps the heart selected when removal fails", async () => {
    api.favorite.mockRejectedValueOnce(new Error("Không thể lưu thay đổi."));
    const food = saved.foods.find((food) => saved.favorites.includes(food.id))!;
    mount(<FoodDetail food={food} onClose={vi.fn()} />);
    const heart = await screen.findByRole("button", {
      name: `Bỏ yêu thích ${food.name}`,
    });
    fireEvent.click(heart);
    await waitFor(() => expect(heart).toBeEnabled());
    expect(heart).toHaveAttribute("aria-pressed", "true");
    expect(saved.favorites).toContain(food.id);
  });
  it("updates the journal chart and insight after marking a dish and after removing the record", async () => {
    const initialVersion = saved.plans[0].version;
    mount(
      <>
        <Dashboard />
        <PlanRevision />
      </>,
    );
    await screen.findByRole("heading", { name: "Gợi ý cho bạn" });
    await screen.findByText("Bạn chưa ghi nhận món đã ăn hôm nay");
    const food = saved.foods.find(
      (food) => food.id === saved.plans[0].items[0].dishId,
    )!;
    fireEvent.click(
      screen.getByRole("button", { name: `Ghi nhận đã ăn ${food.name}` }),
    );
    await screen.findByText("Nhật ký hôm nay đang được cập nhật");
    expect(screen.getByTestId("plan-revision")).toHaveTextContent(
      String(initialVersion + 1),
    );
    expect(api.completeMeal).toHaveBeenCalledWith(
      `${saved.plans[0].id}:${saved.plans[0].items[0].id}`,
      true,
    );
    fireEvent.change(
      screen.getByLabelText("Khoảng thời gian biểu đồ dinh dưỡng"),
      { target: { value: "14" } },
    );
    expect(
      screen.getByRole("img", { name: /Biểu đồ năng lượng 14 ngày/ }),
    ).toBeVisible();
    fireEvent.click(
      screen.getByRole("button", { name: `Bỏ ghi nhận ${food.name}` }),
    );
    await screen.findByText("Bạn chưa ghi nhận món đã ăn hôm nay");
    expect(screen.getByTestId("plan-revision")).toHaveTextContent(
      String(initialVersion + 2),
    );
    expect(api.workspace).toHaveBeenCalledTimes(1);
    expect(api.completeMeal).toHaveBeenLastCalledWith(
      `${saved.plans[0].id}:${saved.plans[0].items[0].id}`,
      false,
    );
  });
  it("shows partial shopping information instead of hiding all ingredients", async () => {
    const item = saved.plans[0].items[0];
    saved.plans[0].items = [item];
    const food = saved.foods.find((food) => food.id === item.dishId)!;
    food.ingredientsComplete = false;
    food.nutrition.sodiumMg = null;
    food.ingredients = [
      { id: "water", name: "Nước", grams: null, sourceAmountText: "200 ml" },
      { id: "rice", name: "Gạo", grams: 100 },
    ];
    mount(<ShoppingPage />);
    await screen.findByText(/Danh sách hiện có một phần nguyên liệu/);
    expect(screen.getByText("Gạo")).toBeVisible();
    expect(screen.getByText(/200 ml/)).toBeVisible();
    expect(screen.getByRole("button", { name: "Tải danh sách" })).toBeEnabled();
    expect(
      screen.queryByText(/đủ nguyên liệu cho thực đơn/),
    ).not.toBeInTheDocument();
  });
  it("offers useful personal settings without developer labels", async () => {
    mount(<SettingsPage />);
    await screen.findByText("a@example.test");
    expect(
      screen.getByRole("button", { name: "Tải bản sao dữ liệu" }),
    ).toBeEnabled();
    expect(document.body.textContent).not.toMatch(
      /Backend|API Gateway|FastAPI|CP-SAT|fixture|bản thử nghiệm/i,
    );
  });
});
