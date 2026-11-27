import { expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { createDemoData } from "../data/seed";
import { FoodLibraryPage } from "../features/catalog/pages/FoodLibraryPage";
const state = createDemoData();
state.foods = [
  { ...state.foods[0],id: "rice",name: "Cơm kiểm thử",category: "MAIN_STARCH",role: "MAIN_STARCH",recipeSource: "NIN_BULK_TEST_RICE",catalogSelectable: true },
  { ...state.foods[1],id: "chicken",name: "Gà kiểm thử",category: "PROTEIN_DISH",role: "PROTEIN_DISH",recipeSource: "NIN_BULK_TEST_CHICKEN",catalogSelectable: true },
];
vi.mock("../lib/config", () => ({ API_MODE: "api", RAG_ENABLED: true }));
vi.mock("../store/UserDataProvider", () => ({ useApp: () => ({ userData: state,update: vi.fn(),toast: vi.fn() }) }));
it("shows Vietnamese categories on filters and cards while filtering the original category values", () => {
  render(<MemoryRouter><FoodLibraryPage /></MemoryRouter>);
  expect(document.body.textContent).not.toMatch(/MAIN_STARCH|PROTEIN_DISH/);
  fireEvent.click(screen.getByRole("button", { name: "Món tinh bột" }));
  expect(screen.getByRole("button", { name: "Cơm kiểm thử" })).toBeVisible();
  expect(screen.queryByRole("button", { name: "Gà kiểm thử" })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Món giàu đạm" }));
  expect(screen.getByRole("button", { name: "Gà kiểm thử" })).toBeVisible();
});
