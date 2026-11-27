const categoryLabels: Record<string, string> = {
  COMPOSITE_DISH: "Món kết hợp",
  MAIN_STARCH: "Món tinh bột",
  PROTEIN_DISH: "Món giàu đạm",
  VEGGIE_DISH: "Rau củ",
  SNACK: "Món ăn nhẹ",
  SOUP: "Canh, súp",
  FRUIT: "Trái cây",
  BEVERAGE: "Đồ uống",
};
export function foodCategoryLabel(value: string) {
  return categoryLabels[value] || (/^[A-Z][A-Z_]+$/.test(value) ? "Món khác" : value);
}
const allergenLabels: Record<string, string> = {
  ALLERGEN_EGG: "Trứng", ALLERGEN_SOY: "Đậu nành", ALLERGEN_DAIRY: "Sữa",
  ALLERGEN_PEANUT: "Đậu phộng", ALLERGEN_SEAFOOD: "Hải sản",
};
export function foodAllergenLabel(value: string) { return allergenLabels[value] || value; }
