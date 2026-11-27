import { Clock3, Heart, ThumbsDown } from "lucide-react";
import { useState } from "react";
import { foodAllergenLabel, foodCategoryLabel } from "../utils/foodLabels";
import type { Food } from "../../../types";
import { useApp } from "../../../store/UserDataProvider";
import { API_MODE } from "../../../lib/config";
import {
  ingredientAmount,
  isSourceFood,
  planningReady,
  safeUrl,
  servingLabel,
  sourceNutritionNote,
  carbohydrateLabel,
} from "../../../utils";
import {
  Alert,
  Badge,
  Button,
  FoodMark,
  FoodSource,
  Modal,
  Nutrition,
} from "../../../components/common/ui";
import { serviceAction, type Action } from "../../../lib/serviceActions";
import type { ReactNode } from "react";
import { ActionForm } from "../../../components/common/ServiceAction";
import {
  AlternativesResult,
  ComparisonResult,
  SafetyResult,
  ServingResult,
  useFoodPreference,
} from "./CatalogTools";

export function FoodDetail({
  food: original,
  onClose,
}: {
  food: Food;
  onClose: () => void;
}) {
  const { userData } = useApp();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const food =
    userData.foods.find((entry) => entry.id === selectedId) ?? original;
  return (
    <Modal title="Chi tiết món ăn" onClose={onClose} wide>
      {food.id !== original.id && (
        <Button variant="ghost" onClick={() => setSelectedId(null)}>
          Quay lại {original.name}
        </Button>
      )}
      <FoodDetailContent key={food.id} food={food} onSelect={setSelectedId} />
    </Modal>
  );
}

function FoodDetailContent({
  food,
  onSelect,
}: {
  food: Food;
  onSelect: (id: string) => void;
}) {
  const { userData } = useApp();
  const [tab, setTab] = useState("ingredients");
  const favorite = userData.favorites.includes(food.id);
  const disliked = userData.disliked?.includes(food.id);
  const { pending, save } = useFoodPreference();
  const busy = pending.includes(food.id);
  let sourceAnnotations: string[] = [];
  let sourceGiStatus = "";
  let sourceGlIsBound = false;
  let sourceGiIsBound = false;
  try {
    const quality = JSON.parse(food.nutritionQualityJson || "{}");
    sourceGiStatus = String(
      quality.perFieldEvidence?.estimatedGi?.giStatus || "",
    );
    sourceGlIsBound = String(
      quality.perFieldEvidence?.glycemicLoad?.method || "",
    ).startsWith("GL_UPPER_BOUND");
    sourceGiIsBound = String(
      quality.perFieldEvidence?.estimatedGi?.method || "",
    ).includes("BOUND");
    sourceAnnotations = (
      Array.isArray(quality.nonQuantifiedSourceAnnotations)
        ? quality.nonQuantifiedSourceAnnotations
        : []
    ).flatMap((item: { name_vi?: unknown }) =>
      typeof item?.name_vi === "string" ? [item.name_vi] : [],
    );
  } catch {
    // Malformed metadata is not nutritional evidence.
  }
  const tools: {
    id: string;
    title: string;
    action: Action;
    fixed: Record<string, string>;
    advanced?: string[];
    render: (value: unknown) => ReactNode;
  }[] = [
    {
      id: "serving",
      title: "Khẩu phần",
      action: serviceAction("catalog", "GET", "dishes/{id}/calculate-serving"),
      fixed: { "{id}": food.id },
      render: (value: unknown) => <ServingResult value={value} carbLabel={carbohydrateLabel([food.carbohydrateDefinition])} />,
    },
    {
      id: "safety",
      title: "Đối chiếu hồ sơ",
      action: serviceAction("catalog", "POST", "dishes/{id}/safety-check"),
      fixed: { "{id}": food.id },
      advanced: ["maxAllowedGlPerMeal", "patientContraindications"],
      render: (value: unknown) => <SafetyResult value={value} />,
    },
    {
      id: "similar",
      title: "Món tương đương",
      action: serviceAction("catalog", "GET", "dishes/{id}/alternatives"),
      fixed: { "{id}": food.id },
      render: (value: unknown) => (
        <AlternativesResult value={value} onSelect={onSelect} />
      ),
    },
    {
      id: "compare",
      title: "So sánh",
      action: serviceAction("catalog", "GET", "dishes/compare"),
      fixed: { "?dish1": food.id },
      render: (value: unknown) => <ComparisonResult value={value} />,
    },
  ];
  const tool =
    API_MODE === "api" ? tools.find((entry) => entry.id === tab) : undefined;
  return (
    <>
      <div className="food-detail-title">
        <FoodMark food={food} />
        <div>
          <Badge tone="neutral">{foodCategoryLabel(food.category)}</Badge>
          <FoodSource food={food} />
          <h2>{food.name}</h2>
          <p>
            <Clock3 size={15} />
            {food.minutes === null
              ? "Chưa có thời gian nấu"
              : `${food.minutes} phút`}{" "}
            <span>·</span>
            {servingLabel(food)}
          </p>
        </div>
        <div className="actions">
          <Button
            variant="secondary"
            onClick={() => void save(food, favorite ? null : "favorite")}
            busy={busy}
            disabled={busy}
            aria-pressed={favorite}
            aria-label={favorite ? `Bỏ yêu thích ${food.name}` : `Yêu thích ${food.name}`}
          >
            <Heart size={17} fill={favorite ? "currentColor" : "none"} />
            {favorite ? "Bỏ yêu thích" : "Yêu thích"}
          </Button>
          {API_MODE === "api" && (
            <Button
              variant="secondary"
              disabled={busy}
              onClick={() => void save(food, disliked ? null : "dislike")}
              aria-pressed={!!disliked}
            >
              <ThumbsDown size={17} />{" "}
              {disliked ? "Bỏ không thích" : "Không thích"}
            </Button>
          )}
        </div>
      </div>
      <p>{food.description}</p>
      {isSourceFood(food) && (
        <div className="stack">
          {!planningReady(food, false, "GENERAL_REFERENCE") && (
            <Alert>
              Đang bổ sung định lượng và dữ liệu dinh dưỡng để dùng món này khi
              tạo hoặc đổi thực đơn.
            </Alert>
          )}
          {planningReady(food, false, "GENERAL_REFERENCE") &&
            !planningReady(food) && (
              <Alert>
                Có dữ liệu dùng ở chế độ Phổ thông; một số chỉ số bệnh lý còn
                chưa biết. Không thay số liệu thiếu bằng 0.
              </Alert>
            )}
          <p className="fineprint">
            {sourceNutritionNote(food)}{" "}
            {(food.grams === null ||
              food.servingWeightBasis ===
                "SOURCE_RAW_IMAGE_AMOUNT_NOT_COOKED") &&
              "Chưa có khối lượng món sau chế biến."}
          </p>
          {sourceGiStatus && (
            <p className="fineprint">
              {sourceGiStatus === "NOT_APPLICABLE"
                ? "GI không áp dụng cho món không có hoặc chỉ có lượng carbohydrate nhỏ; giá trị 0 trong dữ liệu là ký hiệu kỹ thuật, không phải GI được đo."
                : sourceGiIsBound
                  ? "GI dùng giá trị giả định 100 để kiểm tra khi còn thành phần chưa xác định. Đây không phải GI được đo hoặc cận trên sinh lý được bảo đảm; GL cũng dựa trên giả định này."
                  : "GI của món được ước tính từ nguyên liệu và dữ liệu tham chiếu; chưa có phép đo GI của chính món này."}{" "}
              {sourceGlIsBound &&
                "GL dùng giả định kiểm tra GI 100 cho lượng carbohydrate đã công bố, không coi GL bằng 0; đây không phải cận trên đáp ứng sinh lý được bảo đảm."}
            </p>
          )}
          {sourceAnnotations.length > 0 && (
            <p className="fineprint">
              Nguồn không ghi định lượng riêng: {sourceAnnotations.join(", ")}.
              Danh sách mua sắm chỉ gồm các nguyên liệu có định lượng; cần kiểm
              tra thêm gia vị và nước theo công thức nguồn.
            </p>
          )}
          {safeUrl(food.sourceUrl || undefined) && (
            <a
              className="text-link"
              href={safeUrl(food.sourceUrl || undefined)}
              target="_blank"
              rel="noopener noreferrer"
            >
              Xem nguồn Viện Dinh dưỡng
            </a>
          )}
        </div>
      )}
      <Nutrition value={food.nutrition} carbLabel={carbohydrateLabel([food.carbohydrateDefinition])} />
      {food.allergens.length > 0 && (
        <Alert>
          Thành phần có thể gây dị ứng:{" "}
          <strong>{food.allergens.map(foodAllergenLabel).join(", ")}</strong>. Kiểm tra đầy đủ nguyên
          liệu và nguy cơ nhiễm chéo trước khi dùng.
        </Alert>
      )}
      <div className="tabs workflow-tabs" aria-label="Thông tin món ăn">
        <button
          className={tab === "ingredients" ? "active" : ""}
          aria-pressed={tab === "ingredients"}
          onClick={() => setTab("ingredients")}
        >
          Nguyên liệu
        </button>
        <button
          className={tab === "steps" ? "active" : ""}
          aria-pressed={tab === "steps"}
          onClick={() => setTab("steps")}
        >
          Cách chuẩn bị
        </button>
        {API_MODE === "api" &&
          tools.map((entry) => (
            <button
              type="button"
              key={entry.id}
              className={tab === entry.id ? "active" : ""}
              aria-pressed={tab === entry.id}
              onClick={() => setTab(entry.id)}
            >
              {entry.title}
            </button>
          ))}
      </div>
      {tool ? (
        <ActionForm
          key={tool.id}
          action={tool.action}
          fixed={tool.fixed}
          advanced={tool.advanced}
          choices={{
            "?dish2": userData.foods
              .filter((item) => item.listed !== false && item.id !== food.id)
              .map((item) => [item.id, item.name]),
          }}
          renderResult={tool.render}
        />
      ) : tab === "ingredients" ? (
        <div className="ingredient-list">
          {food.ingredientBasis === "source" && (
            <p className="fineprint">
              Định lượng được giữ theo ảnh nguồn; nguồn chưa xác định đầy đủ
              trạng thái sống/chín của nguyên liệu.
            </p>
          )}
          {!food.ingredients.length && (
            <Alert>Chưa có danh sách nguyên liệu cho món này.</Alert>
          )}
          {food.ingredients.map((i) => (
            <div key={i.id}>
              <span>{i.name}</span>
              <strong>{ingredientAmount(i)}</strong>
            </div>
          ))}
        </div>
      ) : (
        <div>
          {food.preparationGuideKind === "method_based" &&
            food.steps.length > 0 && (
              <Alert>
                Gợi ý dựa trên nguyên liệu và phương pháp chế biến trong danh
                mục. Chưa có công thức nấu hoàn chỉnh được kiểm chứng cho món
                này.
              </Alert>
            )}
          <ol className="recipe-steps">
            {!food.steps.length && <li>Chưa có hướng dẫn nấu cho món này.</li>}
            {food.steps.map((s) => (
              <li key={s}>{s}</li>
            ))}
          </ol>
        </div>
      )}
      {API_MODE === "demo" && (
        <p className="fineprint">
          Công thức và dinh dưỡng minh họa để thử giao diện; cần thay bằng dữ
          liệu chuẩn hóa của Food Service trước khi sử dụng thực tế.
        </p>
      )}
    </>
  );
}
