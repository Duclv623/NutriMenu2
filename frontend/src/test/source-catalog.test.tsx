import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { createDemoData } from "../data/seed";
import type { Food, UserData } from "../types";
import { FoodDetail } from "../features/catalog/components/FoodDetail";
import { FoodLibraryPage as FoodLibrary } from "../features/catalog/pages/FoodLibraryPage";
import { MealPlannerPage as MealPlanner } from "../features/meals/pages/MealPlannerPage";
import { MealChangeModal } from "../features/meals/components/MealChangeModal";
import {
  allowedFood,
  ingredientAmount,
  planningReady,
  servingLabel,
  shoppingList,
  sumNutrition,
} from "../utils";
import { generateDemo } from "../features/meals/services/demoMealPlan";

let workspace: UserData;
let sourceFood: Food;
const { preview } = vi.hoisted(() => ({ preview: vi.fn() }));
vi.mock("../store/UserDataProvider", () => ({
  useApp: () => ({ userData: workspace, update: vi.fn(), toast: vi.fn() }),
}));
vi.mock("../features/meals/services/mealPlanService", async (load) => {
  const actual = await load<typeof import("../features/meals/services/mealPlanService")>();
  return {
    ...actual,
    API_MODE: "api",
    mealPlanService: { ...actual.mealPlanService, previewSwap: preview },
  };
});
beforeEach(() => {
  vi.clearAllMocks();
  workspace = structuredClone(createDemoData());
  sourceFood = {
    ...workspace.foods[0],
    id: "nin-source",
    name: "Món nguồn kiểm thử",
    sourceRecordId: "source-1",
    recipeSource: "NIN_REAL_TEST_SOURCE",
    sourceUrl: "https://viendinhduong.vn/source",
    image: "https://viendinhduong.vn/real-source.webp",
    solverEligible: false,
    grams: null,
    householdUnitLabel: "1 phần theo ảnh nguồn",
    nutritionBasis: "SOURCE_SERVING_INFERRED_WITH_EXPLICIT_PROXIES",
    carbohydrateDefinition: "UNSPECIFIED_SOURCE_GLUCID",
    ingredientBasis: "source",
    nutrition: { ...workspace.foods[0].nutrition, sodiumMg: null },
    ingredients: [
      {
        id: "water",
        name: "Nước",
        grams: null,
        sourceAmountText: "Nước: 200 ml (*)",
      },
    ],
  };
});

describe("source catalog display and planning boundary", () => {
  it("distinguishes a screening GI assumption from a measured value and preserves annotations", () => {
    sourceFood.nutritionQualityJson = JSON.stringify({
      perFieldEvidence: {
        estimatedGi: {
          method: "UNRESOLVED_COMPONENT_RECIPE_GI100_BOUND_ESTIMATE",
          giStatus: "ESTIMATED_NOT_MEASURED",
        },
      },
      nonQuantifiedSourceAnnotations: [{ name_vi: "Gia vị không định lượng" }],
    });
    render(<FoodDetail food={sourceFood} onClose={vi.fn()} />);
    expect(screen.getByText(/GI dùng giá trị giả định 100/)).toBeVisible();
    expect(screen.getByText(/cận trên sinh lý được bảo đảm/)).toBeVisible();
    expect(
      screen.getByText(/Nguồn không ghi định lượng riêng/),
    ).toHaveTextContent("Gia vị không định lượng");
  });

  it("explains the N/A sentinel without presenting trace carbohydrate GL as zero", () => {
    sourceFood.nutritionQualityJson = JSON.stringify({
      perFieldEvidence: {
        estimatedGi: { giStatus: "NOT_APPLICABLE" },
        glycemicLoad: { method: "GL_UPPER_BOUND_TRACE_CARBOHYDRATE" },
      },
    });
    render(<FoodDetail food={sourceFood} onClose={vi.fn()} />);
    expect(screen.getByText(/GI không áp dụng/)).toHaveTextContent(
      "không phải GI được đo",
    );
    expect(screen.getByText(/GI không áp dụng/)).toHaveTextContent(
      "không coi GL bằng 0",
    );
  });

  it("shows the API photo, exact source quantity, pending status and missing nutrition", () => {
    render(<FoodDetail food={sourceFood} onClose={vi.fn()} />);
    expect(screen.getByRole("img", { name: sourceFood.name })).toHaveAttribute(
      "src",
      sourceFood.image,
    );
    expect(screen.getByText("Viện Dinh dưỡng")).toBeVisible();
    expect(screen.getByText("Glucid theo nguồn NIN")).toBeVisible();
    expect(screen.getByText("Chờ bổ sung dữ liệu")).toBeVisible();
    expect(screen.getByText("Nước: 200 ml (*)")).toBeVisible();
    expect(
      screen.getByText(/nguồn chưa xác định đầy đủ trạng thái sống\/chín/),
    ).toBeVisible();
    expect(screen.getByText(/1 phần theo ảnh nguồn/)).toBeVisible();
    const sodium = screen.getByText("Natri").parentElement!;
    expect(within(sodium).getByText("Chưa có")).toBeVisible();
    expect(within(sodium).queryByText("0")).not.toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Xem nguồn Viện Dinh dưỡng" }),
    ).toHaveAttribute("rel", "noopener noreferrer");
  });

  it("keeps pending source dishes browsable and filters by source without inventing foods", () => {
    workspace.foods = [sourceFood, workspace.foods[0]];
    render(<FoodLibrary />);
    expect(document.querySelectorAll(".food-card")).toHaveLength(1);
    expect(
      screen.queryByRole("button", { name: workspace.foods[1].name }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("img", { name: sourceFood.name })).toHaveAttribute(
      "src",
      sourceFood.image,
    );
    fireEvent.change(screen.getByLabelText("Nguồn dữ liệu"), {
      target: { value: "nin" },
    });
    expect(screen.getByRole("button", { name: sourceFood.name })).toBeVisible();
    expect(document.querySelectorAll(".food-card")).toHaveLength(1);
    expect(allowedFood(sourceFood, workspace.profile)).toBe(true);
  });

  it("disables changing an incomplete source dish already in a plan", () => {
    workspace.foods = [sourceFood];
    const plan = workspace.plans[0];
    plan.date = "2026-11-24";
    plan.items = [{ ...plan.items[0], dishId: sourceFood.id }];
    render(
      <MemoryRouter initialEntries={["/thuc-don?date=2026-11-24"]}>
        <MealPlanner />
      </MemoryRouter>,
    );
    expect(document.querySelector(".planned-actions button")).toBeDisabled();
    expect(screen.getByRole("img", { name: sourceFood.name })).toHaveAttribute(
      "src",
      sourceFood.image,
    );
    expect(screen.getByText("Chờ bổ sung dữ liệu")).toBeVisible();
  });

  it("keeps historical totals readable and clearly labels an old API plan", () => {
    const plan = workspace.plans[0];
    plan.dataMode = "api";
    plan.date = "2026-11-24";
    const totals = sumNutrition(plan.items, workspace.foods);
    expect(totals.energyKcal).toBeGreaterThan(0);
    render(
      <MemoryRouter initialEntries={["/thuc-don?date=2026-11-24"]}>
        <MealPlanner />
      </MemoryRouter>,
    );
    expect(screen.getByText("Thực đơn dùng dữ liệu cũ")).toBeVisible();
    expect(document.querySelector(".planned-actions button")).toBeDisabled();
  });

  it("does not offer pending or metadata-incomplete source dishes for a change", () => {
    workspace.foods = [
      sourceFood,
      {
        ...sourceFood,
        id: "unknown",
        name: "Chưa có xác nhận",
        solverEligible: undefined,
      },
    ];
    const plan = workspace.plans[0];
    render(
      <MealChangeModal plan={plan} item={plan.items[0]} onClose={vi.fn()} />,
    );
    expect(
      screen.queryByRole("option", { name: sourceFood.name }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("option", { name: "Chưa có xác nhận" }),
    ).not.toBeInTheDocument();
    expect(document.querySelector(".modal-actions .primary")).toBeDisabled();
    expect(preview).not.toHaveBeenCalled();
  });

  it("propagates unknown totals and blocks unknown quantities in shopping and demo generation", () => {
    workspace.foods = [sourceFood];
    workspace.plans[0].items = [
      { ...workspace.plans[0].items[0], dishId: sourceFood.id },
    ];
    expect(
      sumNutrition(workspace.plans[0].items, workspace.foods).sodiumMg,
    ).toBeNull();
    expect(() => shoppingList(workspace.plans[0], workspace.foods)).toThrow();
    expect(() =>
      generateDemo(
        {
          date: "2026-11-24",
          target: 1800,
          snacks: true,
          maxMinutes: 45,
          excludeRepeat: false,
        },
        workspace,
      ),
    ).toThrow();
    expect(servingLabel(sourceFood)).not.toMatch(/0 g/);
    expect(ingredientAmount(sourceFood.ingredients[0], 2)).toBe(
      "Nước: 200 ml (*) × 2",
    );
  });

  it("rejects legacy production selection while explicit demo and approved complete source records remain separate", () => {
    expect(planningReady(workspace.foods[0])).toBe(false);
    expect(planningReady(workspace.foods[0], true)).toBe(true);
    expect(
      planningReady({
        ...workspace.foods[0],
        nutrition: {} as Food["nutrition"],
      }),
    ).toBe(false);
    expect(
      planningReady({ ...workspace.foods[0], recipeSource: "NIN_REAL_OLD" }),
    ).toBe(false);
    expect(planningReady(sourceFood)).toBe(false);
    expect(planningReady({ ...sourceFood, solverEligible: true })).toBe(false);
    expect(
      planningReady({
        ...sourceFood,
        solverEligible: true,
        nutrition: workspace.foods[0].nutrition,
      }),
    ).toBe(true);
  });
});

vi.mock("../lib/config", () => ({ API_MODE: "api", RAG_ENABLED: true }));
