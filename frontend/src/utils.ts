import type {
  CatalogNutrients,
  Food,
  Ingredient,
  Nutrients,
  Plan,
  PlanItem,
  Profile,
} from "./types";
export function localDate(value = new Date()) {
  const d = new Date(value);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
export function dateLabel(
  date: string,
  options: Intl.DateTimeFormatOptions = {
    weekday: "long",
    day: "numeric",
    month: "long",
  },
) {
  return new Intl.DateTimeFormat("vi-VN", options).format(
    new Date(date + "T12:00:00"),
  );
}
export function addDays(date: string, count: number) {
  const d = new Date(date + "T12:00:00");
  d.setDate(d.getDate() + count);
  return localDate(d);
}
export function carbohydrateLabel(definitions: (string | null | undefined)[]): string {
  const glucid = definitions.filter(value => value?.startsWith("UNSPECIFIED_SOURCE_GLUCID")).length;
  if (!glucid) return "Carbohydrate";
  return glucid === definitions.length ? "Glucid theo nguồn NIN" : "Carbohydrate (có Glucid theo nguồn NIN)";
}

export function planCarbohydrateLabel(items: PlanItem[], foods: Food[]): string {
  return carbohydrateLabel(items.map(item =>
    item.carbohydrateDefinition ?? foods.find(food => food.id === item.dishId)?.carbohydrateDefinition,
  ));
}

export const number = (n: number | null | undefined, digits = 0) =>
  n === null || n === undefined || !Number.isFinite(n)
    ? "Chưa có"
    : new Intl.NumberFormat("vi-VN", { maximumFractionDigits: digits }).format(
        n,
      );
export const scaleAmount = (amount: number | null | undefined, servingMultiplier = 1) =>
  typeof amount !== "number" ||
  !Number.isFinite(amount) ||
  !Number.isFinite(servingMultiplier)
    ? null
    : amount * servingMultiplier;
export const isSourceFood = (food: Food) =>
  !!food.recipeSource && /^NIN_(REAL|BULK)_/.test(food.recipeSource);
export function servingLabel(food: Food, servingMultiplier = 1) {
  const grams = scaleAmount(food.grams, servingMultiplier);
  if (grams !== null)
    return `${number(grams)} g${food.servingWeightBasis === "SOURCE_RAW_IMAGE_AMOUNT_NOT_COOKED" ? " theo định lượng nguồn" : ""}`;
  const label = food.householdUnitLabel?.replace(
    "theo ảnh nguồn (cơ sở có suy luận)",
    "tham khảo",
  );
  if (servingMultiplier !== 1 && label) return `${number(servingMultiplier, 2)} × ${label}`;
  return label || "Chưa có khối lượng khẩu phần";
}
export function ingredientAmount(ingredient: Ingredient, servingMultiplier = 1) {
  if (ingredient.sourceAmountText && servingMultiplier === 1)
    return ingredient.sourceAmountText;
  const grams = scaleAmount(ingredient.grams, servingMultiplier);
  if (grams !== null) return `${number(grams, 1)} g`;
  if (ingredient.sourceAmountText)
    return `${ingredient.sourceAmountText} × ${number(servingMultiplier, 2)}`;
  return "Chưa có định lượng";
}
export function catalogVisible(food: Food, demo = false) {
  return (
    food.catalogSelectable !== false &&
    !food.isHistoricalOnly &&
    (demo || isSourceFood(food))
  );
}
export function planUsesHistoricalData(plan: Plan, foods: Food[]) {
  return (
    plan.dataMode === "api" &&
    plan.items.some((item) => {
      const food = foods.find((f) => f.id === item.dishId);
      return !food || food.isHistoricalOnly || !isSourceFood(food);
    })
  );
}
export function planningReady(
  food: Food,
  demo = false,
  mode: import("./types").PlanningMode = "MEDICAL",
) {
  return (
    catalogVisible(food, demo) &&
    food.solverEligible !== false &&
    (!isSourceFood(food) || food.solverEligible === true) &&
    (mode === "GENERAL_REFERENCE"
      ? (["energyKcal", "netCarbG", "proteinG", "fatG", "sodiumMg"] as const)
      : (["energyKcal", "netCarbG", "proteinG", "fatG", "fiberG", "sodiumMg"] as const)
    ).every((key) => {
      const value = food.nutrition[key];
      return typeof value === "number" && Number.isFinite(value) && value >= 0;
    })
  );
}
export function assertNewSourceSelection(plan: Plan, foods: Food[]) {
  if (
    !plan.items.length ||
    plan.items.some((item) => {
      const food = foods.find((f) => f.id === item.dishId);
      return !food || !planningReady(food, false, plan.planningMode);
    })
  )
    throw new Error(
      "Kết quả có món không thuộc dữ liệu mới đủ điều kiện. Thực đơn chưa được chấp nhận.",
    );
}
export function sourceNutritionNote(food: Food) {
  let estimated = false;
  try {
    const quality = JSON.parse(food.nutritionQualityJson || "{}");
    estimated = Object.values(quality.perFieldEvidence || {}).some(
      (evidence) =>
        typeof evidence === "object" &&
        evidence !== null &&
        /PROXY|ESTIMAT/.test(
          String((evidence as { method?: unknown }).method || ""),
        ),
    );
  } catch {
    /* A malformed quality record does not become evidence. */
  }
  return `${food.nutritionBasis?.includes("INFERRED") ? "Khẩu phần được suy ra từ ảnh nguồn, có ước tính." : "Dữ liệu dinh dưỡng theo thông tin nguồn hiện có."}${estimated ? " Một số chỉ số dinh dưỡng dùng dữ liệu ước tính bổ sung." : ""}`;
}
export const uid = () => crypto.randomUUID();
export const emptyNutrition = (): Nutrients => ({
  energyKcal: 0,
  netCarbG: 0,
  proteinG: 0,
  fatG: 0,
  fiberG: 0,
  sodiumMg: 0,
});
export function sumNutrition(
  items: PlanItem[],
  foods: Food[],
): CatalogNutrients {
  return items.reduce((total, item) => {
    const food = foods.find((f) => f.id === item.dishId);
    if (!food && !item.nutrition)
      throw new Error(
        `Thiếu dữ liệu món ${item.dishId}. Không thể tính tổng dinh dưỡng.`,
      );
    for (const key of Object.keys(total) as (keyof Nutrients)[]) {
      const value = item.nutrition
        ? scaleAmount(item.nutrition[key])
        : scaleAmount(food!.nutrition[key], item.servingMultiplier);
      total[key] =
        total[key] === null || value === null ? null : total[key]! + value;
    }
    return total;
  }, emptyNutrition() as CatalogNutrients);
}
export function allowedFood(food: Food, profile: Profile) {
  const normalize = (s: string) => s.trim().toLocaleLowerCase("vi");
  const allergyCodes: Record<string, string> = {
    "Hải sản": "ALLERGEN_SEAFOOD",
    Trứng: "ALLERGEN_EGG",
    Sữa: "ALLERGEN_DAIRY",
    "Đậu nành": "ALLERGEN_SOY",
    "Đậu phộng": "ALLERGEN_PEANUT",
  };
  // Unknown labels and missing recipes cannot be shown as a confirmed match.
  if (
    food.ingredientsComplete !== undefined &&
    profile.allergies.some(
      (a) => !allergyCodes[a] && !Object.values(allergyCodes).includes(a),
    )
  )
    return false;
  if (
    food.ingredientsComplete === false &&
    (profile.allergies.length || profile.excludedFoods.length)
  )
    return false;
  const excludedAllergens = new Set(
    profile.allergies.flatMap((a) => [a, allergyCodes[a] || a]),
  );
  return (
    !food.allergens.some((a) => excludedAllergens.has(a)) &&
    !profile.excludedFoods.some((ex) =>
      [food.name, ...food.ingredients.map((i) => i.name)].some((name) =>
        normalize(name).includes(normalize(ex)),
      ),
    )
  );
}
export function mealProfileIssue(profile: Profile) {
  if (profile.age < 18)
    return "Chức năng tạo thực đơn dành cho người từ đủ 18 tuổi.";
  if (profile.pregnant || profile.specialDiet)
    return "Tình trạng đã chọn trong mục Phạm vi hỗ trợ nằm ngoài phạm vi tạo thực đơn của ứng dụng.";
  if (!profile.confirmed)
    return "Hồ sơ chưa được xác nhận. Trong mục Phạm vi hỗ trợ, hãy tích ô “Tôi đã kiểm tra thông tin sức khỏe, dị ứng và món không sử dụng” rồi bấm Lưu hồ sơ.";
  return "";
}
export function assertEligible(profile: Profile) {
  if (profile.age < 18 || profile.pregnant || profile.specialDiet)
    throw new Error(
      "Hồ sơ ngoài phạm vi hỗ trợ. Vui lòng trao đổi trực tiếp với bác sĩ hoặc chuyên gia dinh dưỡng.",
    );
  if (!profile.confirmed)
    throw new Error(
      "Vui lòng xác nhận thông tin dị ứng và tình trạng sức khỏe trong hồ sơ.",
    );
}
export function shoppingList(plan: Plan, foods: Food[]) {
  const summary = shoppingDetails(plan, foods);
  if (
    summary.incompleteFoods.length ||
    summary.ingredients.some((i) => i.grams === null || i.details.length)
  )
    throw new Error(
      "Một số nguyên liệu chưa đủ định lượng theo gam. Xem danh sách mua sắm để kiểm tra từng nguyên liệu.",
    );
  return summary.ingredients.map((i) => ({
    id: i.id,
    name: i.name,
    grams: i.grams!,
  }));
}

export function shoppingDetails(plan: Plan, foods: Food[]) {
  const map = new Map<
    string,
    { id: string; name: string; grams: number | null; details: string[] }
  >();
  const incompleteFoods = new Set<string>();
  for (const item of plan.items) {
    const food = foods.find((f) => f.id === item.dishId);
    if (!food) {
      incompleteFoods.add(`Món ${item.dishId}`);
      continue;
    }
    if (food.ingredientsComplete === false || !food.ingredients.length)
      incompleteFoods.add(food.name);
    try {
      const quality = JSON.parse(food.nutritionQualityJson || "{}");
      if (
        Array.isArray(quality.nonQuantifiedSourceAnnotations) &&
        quality.nonQuantifiedSourceAnnotations.length
      ) {
        incompleteFoods.add(food.name);
      }
    } catch {
      incompleteFoods.add(food.name);
    }
    if (!Number.isFinite(item.servingMultiplier) || item.servingMultiplier <= 0) {
      incompleteFoods.add(food.name);
      continue;
    }
    for (const ingredient of food.ingredients) {
      const current = map.get(ingredient.id) || {
        id: ingredient.id,
        name: ingredient.name,
        grams: null,
        details: [],
      };
      if (
        ingredient.grams !== null &&
        Number.isFinite(ingredient.grams) &&
        ingredient.grams >= 0
      ) {
        current.grams = (current.grams || 0) + ingredient.grams * item.servingMultiplier;
      } else {
        // Preserve source units (ml, quả, thìa...) rather than inventing grams.
        current.details.push(
          `${food.name}: ${ingredientAmount(ingredient, item.servingMultiplier)}`,
        );
        if (!ingredient.sourceAmountText) incompleteFoods.add(food.name);
      }
      map.set(ingredient.id, current);
    }
  }
  return {
    ingredients: [...map.values()].sort((a, b) =>
      a.name.localeCompare(b.name, "vi"),
    ),
    incompleteFoods: [...incompleteFoods],
  };
}
export function downloadFile(
  filename: string,
  content: string,
  type = "text/plain;charset=utf-8",
) {
  const url = URL.createObjectURL(new Blob(["\uFEFF", content], { type }));
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export function safeUrl(value?: string) {
  if (!value) return undefined;
  try {
    const u = new URL(value, window.location.origin);
    return ["http:", "https:"].includes(u.protocol) ? u.href : undefined;
  } catch {
    return undefined;
  }
}
