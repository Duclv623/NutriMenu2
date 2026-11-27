export type Nutrients = {
  energyKcal: number;
  netCarbG: number;
  proteinG: number;
  fatG: number;
  fiberG: number;
  sodiumMg: number;
};
export type MealType = "breakfast" | "lunch" | "dinner" | "snack";
export type CatalogNutrients = { [K in keyof Nutrients]: number | null };
export type Ingredient = {
  id: string;
  name: string;
  grams: number | null;
  sourceAmountText?: string | null;
};
export type Food = {
  giLevel?: "LOW" | "MEDIUM" | "HIGH" | "UNKNOWN";
  listed?: boolean;
  id: string;
  name: string;
  category: string;
  description: string;
  emoji: string;
  color: string;
  image: string;
  minutes: number | null;
  grams: number | null;
  meals: MealType[];
  allergens: string[];
  ingredients: Ingredient[];
  nutrition: CatalogNutrients;
  steps: string[];
  preparationGuideKind?: string;
  role?: string;
  servingRules?: { min: number; max: number; step: number };
  ingredientsComplete?: boolean;
  ingredientBasis?: "raw" | "cooked" | "source";
  sourceRecordId?: string | null;
  recipeSource?: string;
  sourceUrl?: string | null;
  nutritionBasis?: string | null;
  carbohydrateDefinition?: string | null;
  nutritionQualityJson?: string | null;
  solverEligible?: boolean | null;
  catalogSelectable?: boolean;
  isHistoricalOnly?: boolean;
  servingWeightBasis?: string | null;
  householdUnitLabel?: string | null;
};
export type Profile = {
  name: string;
  age: number;
  dateOfBirth?: string;
  gender: string;
  height: number;
  weight: number;
  targetWeight: number;
  activityLevel: string;
  goal: string;
  allergies: string[];
  excludedFoods: string[];
  preferences: string;
  pregnant: boolean;
  specialDiet: boolean;
  confirmed: boolean;
  version: number;
};
export type PlanItem = {
  completed?: boolean;
  name?: string;
  id: string;
  dishId: string;
  meal: MealType;
  servingMultiplier: number;
  locked: boolean;
  // Snapshot for this item including servingMultiplier; supplied by the optimizer.
  nutrition?: CatalogNutrients;
  carbohydrateDefinition?: string | null;
};
export type SolverStatus =
  | "OPTIMAL"
  | "FEASIBLE"
  | "INFEASIBLE"
  | "UNKNOWN"
  | "MODEL_INVALID"
  | "NOT_RUN";
export type PlanningMode = "MEDICAL" | "GENERAL_REFERENCE";
export type Plan = {
  planningMode?: PlanningMode;
  skippedItems?: PlanItem[];
  rating?: number | null;
  feedback?: string | null;
  id: string;
  date: string;
  profileVersion: number;
  version: number;
  items: PlanItem[];
  solverStatus: SolverStatus;
  dataMode: "demo" | "api";
  target: number;
  notes: string[];
  createdAt: string;
  changeSignature?: string;
  validationPassed?: boolean;
};
export type GenerateInput = {
  planningMode?: PlanningMode;
  replacePlanId?: string;
  baseVersion?: number;
  replacementConfirmed?: boolean;
  date: string;
  target: number;
  snacks: boolean;
  maxMinutes: number;
  excludeRepeat: boolean;
};
export type User = { role?: string; id: string; name: string; email: string; demo: boolean };
export type Citation = {
  id: string;
  title: string;
  quote: string;
  source_url: string;
  document_url?: string;
  section?: string;
  pdf_pages?: number[];
};
export type Message = {
  id: string;
  role: "user" | "assistant";
  text: string;
  citations?: Citation[];
  warnings?: string[];
  feedback?: string;
  remoteId?: string;
  historySaved?: boolean;
  mealResult?: MealResult;
};
export type Conversation = {
  id: string;
  title: string;
  messages: Message[];
  sessionId?: string;
  updatedAt: string;
  loaded?: boolean;
  planId?: string;
};
export type CheckIn = {
  id: string;
  date: string;
  weight: number;
  glucose?: number;
  note: string;
};
export type UserData = {
  profile: Profile;
  foods: Food[];
  plans: Plan[];
  favorites: string[];
  disliked?: string[];
  checkedShopping: string[];
  checkIns: CheckIn[];
  conversations: Conversation[];
  completedMeals: string[];
  capabilities?: {
    weeklyGeneration?: boolean;
    generate: boolean;
    regenerate?: boolean;
    swap: boolean;
    favoriteRemoval: boolean;
    cookingTime: boolean;
    atomicVersions: boolean;
    reason?: string;
  };
};

export type MealResult = {
  editor?: {
    planId: string;
    baseVersion: number;
    itemIds: string[];
    instruction: string;
    excludedTerms?: string[];
    preferredTerms?: string[];
    requestedServings?: number;
  };
  dataMode?: string;
  context?: {
    plan_id: string;
    plan_version?: number;
    item_id?: string;
    meal_type?: string;
  };
  choices?: { itemId: string; label: string; action: string }[];
  proposal?: {
    id: string;
    planId: string;
    baseVersion: number;
    expiresAt: number;
  } | null;
};
