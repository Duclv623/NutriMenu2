import { API_MODE } from "../../../lib/config";
import { mealPlanService } from "../services/mealPlanService";
import { useState } from "react";

import { Link } from "react-router-dom";

import { ArrowRight, Search, Trash2 } from "lucide-react";

import { Badge, Confirm, Empty, IconButton, PageTitle } from "../../../components/common/ui";

import { useApp } from "../../../store/UserDataProvider";



import { dateLabel, number, sumNutrition } from "../../../utils";

import type { Plan } from "../../../types";
import { TableCell } from "../../../components/common/TableCell";

import { OptimizerHistoryTools } from "../components/OptimizerHistoryTools";

export function HistoryPage() {
  const { userData: w, update, toast } = useApp();
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("all");
  const [remove, setRemove] = useState<Plan | null>(null);
  const plans = w.plans
    .filter(
      (p) =>
        (!query ||
          p.date.includes(query) ||
          p.items.some((i) =>
            w.foods
              .find((f) => f.id === i.foodId)
              ?.name.toLowerCase()
              .includes(query.toLowerCase()),
          )) &&
        (filter === "all" ||
          (filter === "demo" ? p.dataMode === "demo" : p.dataMode === "api")),
    )
    .sort(
      (a, b) =>
        b.date.localeCompare(a.date) || b.createdAt.localeCompare(a.createdAt),
    );
  async function deletePlan() {
    if (!remove) return;
    if (API_MODE === "api") await mealPlanService.deletePlan(remove.id);
    update((s) => ({
      ...s,
      plans: s.plans.filter((p) => p.id !== remove.id),
      completedMeals: s.completedMeals.filter(
        (k) => !k.startsWith(remove.id + ":"),
      ),
      checkedShopping: s.checkedShopping.filter(
        (k) => !k.startsWith(remove.id + ":"),
      ),
    }));
    toast("Đã xóa thực đơn khỏi lịch sử.");
  }
  return (
    <div className="stack-lg">
      <PageTitle
        eyebrow="NHÌN LẠI NHỮNG BỮA ĂN"
        title="Lịch sử thực đơn"
        description="Mỗi kế hoạch đều được lưu cùng ngày và phiên bản hồ sơ."
      />
      {API_MODE === "api" && <OptimizerHistoryTools />}
      <div className="panel library-filters">
        <div className="search-input">
          <Search size={17} />
          <input
            aria-label="Tìm thực đơn"
            placeholder="Tìm theo tên món hoặc ngày YYYY-MM-DD…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
        <select
          aria-label="Loại thực đơn"
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
        >
          <option value="all">Tất cả thực đơn</option>
          <option value="demo">Thực đơn demo</option>
          <option value="api">Thực đơn từ API</option>
        </select>
      </div>
      {plans.length ? (
        <div className="panel table-wrap">
          <table role="table" aria-label="Lịch sử thực đơn">
            <thead role="rowgroup">
              <tr role="row">
                <th role="columnheader" scope="col">
                  Ngày
                </th>
                <th role="columnheader" scope="col">
                  Bữa ăn
                </th>
                <th role="columnheader" scope="col">
                  Tổng năng lượng
                </th>
                <th role="columnheader" scope="col">
                  Trạng thái
                </th>
                <th role="columnheader" scope="col">
                  Hồ sơ
                </th>
                <th role="columnheader" scope="col">
                  <span className="sr-only">Thao tác</span>
                </th>
              </tr>
            </thead>
            <tbody role="rowgroup">
              {plans.map((p) => (
                <tr key={p.id} role="row">
                  <TableCell label="Ngày">
                    <Link to={`/thuc-don?date=${p.date}&plan=${p.id}`}>
                      {dateLabel(p.date, {
                        weekday: "short",
                        day: "numeric",
                        month: "numeric",
                        year: "numeric",
                      })}
                    </Link>
                    <small>
                      {new Date(p.createdAt).toLocaleTimeString("vi-VN", {
                        hour: "2-digit",
                        minute: "2-digit",
                      })}
                    </small>
                  </TableCell>
                  <TableCell label="Bữa ăn">{p.items.length} món</TableCell>
                  <TableCell label="Tổng năng lượng">
                    <strong>
                      {number(sumNutrition(p.items, w.foods).energy)}
                    </strong>{" "}
                    kcal
                  </TableCell>
                  <TableCell label="Trạng thái">
                    <Badge tone={p.dataMode === "demo" ? "amber" : "green"}>
                      {p.dataMode === "demo" ? "Mẫu giao diện" : p.solverStatus}
                    </Badge>
                  </TableCell>
                  <TableCell label="Hồ sơ">
                    v{p.profileVersion}
                    {p.profileVersion !== w.profile.version && (
                      <small>Hồ sơ đã cập nhật</small>
                    )}
                  </TableCell>
                  <TableCell label="Thao tác">
                    <div className="actions">
                      <Link
                        className="text-link"
                        to={`/thuc-don?date=${p.date}&plan=${p.id}`}
                      >
                        Xem
                        <ArrowRight size={14} />
                      </Link>
                      <IconButton
                        label={`Xóa thực đơn ${p.date}`}
                        onClick={() => setRemove(p)}
                      >
                        <Trash2 size={16} />
                      </IconButton>
                    </div>
                  </TableCell>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <Empty title="Chưa có thực đơn phù hợp">
          Thử đổi bộ lọc hoặc tạo một thực đơn mới.
        </Empty>
      )}
      {remove && (
        <Confirm
          title="Xóa thực đơn này?"
          description="Thực đơn và dấu ghi nhận liên quan sẽ bị xóa. Hồ sơ và các thực đơn khác được giữ nguyên."
          onClose={() => setRemove(null)}
          onConfirm={deletePlan}
        />
      )}
    </div>
  );
}
