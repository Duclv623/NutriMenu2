import { useState } from "react";
import { ArrowRight, Sparkles } from "lucide-react";
import { Link } from "react-router-dom";
import type { UserData } from "../../../types";
import {
  dailySummaries,
  dashboardInsights,
  recordedAverage,
  type DaySummary,
} from "../../../insights";
import { dateLabel, number } from "../../../utils";
import { Empty } from "../../../components/common/ui";

export function InsightPanel({
  workspace,
  today,
}: {
  workspace: UserData;
  today: string;
}) {
  const insights = dashboardInsights(
    workspace,
    dailySummaries(workspace, today),
    today,
  );
  return (
    <section className="panel insight-panel" aria-labelledby="insights-title">
      <div className="section-title">
        <h2 id="insights-title">
          <Sparkles size={19} />
          Gợi ý cho bạn
        </h2>
      </div>
      <p className="muted insight-intro">
        Dựa trên hồ sơ, thực đơn và nhật ký của bạn.
      </p>
      <div className="insight-list">
        {insights.map((insight) => (
          <article
            key={insight.id}
            className={`insight-item insight-${insight.tone}`}
          >
            <strong>{insight.title}</strong>
            <p>{insight.description}</p>
            <Link to={insight.to}>
              {insight.action} <ArrowRight size={14} />
            </Link>
          </article>
        ))}
      </div>
    </section>
  );
}

export function IntakeTrends({
  workspace,
  today,
}: {
  workspace: UserData;
  today: string;
}) {
  const [range, setRange] = useState(7);
  const days = dailySummaries(workspace, today, range);
  const recordedDays = days.filter((day) => day.recordedCount > 0).length;
  const completeDays = days.filter(
    (day) => day.itemCount > 0 && day.recordedCount === day.itemCount,
  ).length;
  return (
    <section className="panel intake-trends" aria-labelledby="intake-title">
      <div className="section-title trend-heading">
        <div>
          <div className="eyebrow">NHẬT KÝ DINH DƯỠNG</div>
          <h2 id="intake-title">Kế hoạch và phần đã ghi nhận</h2>
          <p>So sánh năng lượng theo từng ngày, tính bằng kcal.</p>
        </div>
        <label className="trend-range">
          Khoảng thời gian
          <select
            aria-label="Khoảng thời gian biểu đồ dinh dưỡng"
            value={range}
            onChange={(e) => setRange(Number(e.target.value))}
          >
            <option value={7}>7 ngày</option>
            <option value={14}>14 ngày</option>
            <option value={30}>30 ngày</option>
          </select>
        </label>
      </div>
      <div className="trend-stats">
        <div>
          <span>Ngày có ghi nhận</span>
          <strong>
            {recordedDays}
            <small> / {range} ngày</small>
          </strong>
        </div>
        <div>
          <span>Ngày ghi nhận đủ món</span>
          <strong>
            {completeDays}
            <small> ngày</small>
          </strong>
        </div>
        <div>
          <span>Trung bình đã ghi nhận</span>
          <strong>
            {number(recordedAverage(days))}
            <small> kcal/ngày có dữ liệu</small>
          </strong>
        </div>
      </div>
      <IntakeChart days={days} />
      <p className="dashboard-data-note">
        “Chưa ghi nhận” không có nghĩa là chưa ăn. Dinh dưỡng ước tính theo khẩu
        phần trong thực đơn đã xác nhận, chưa bao gồm món ăn thêm bên ngoài.
        Trung bình chỉ tính ngày có số liệu năng lượng, kể cả ngày mới ghi nhận
        một phần.
      </p>
      <details className="trend-table">
        <summary>Xem số liệu từng ngày</summary>
        <div className="table-wrap">
          <table>
            <caption className="sr-only">
              Năng lượng theo ngày, đơn vị kcal
            </caption>
            <thead>
              <tr>
                <th>Ngày</th>
                <th>Mục tiêu</th>
                <th>Theo thực đơn</th>
                <th>Đã ghi nhận</th>
                <th>Món đã ghi nhận</th>
              </tr>
            </thead>
            <tbody>
              {days.map((day) => (
                <tr key={day.date}>
                  <td>
                    <Link to={`/thuc-don?date=${day.date}`}>
                      {dateLabel(day.date, {
                        day: "numeric",
                        month: "numeric",
                      })}
                    </Link>
                  </td>
                  <td>{number(day.target)}</td>
                  <td>{number(day.planned?.energyKcal)}</td>
                  <td>
                    {day.recordedCount
                      ? number(day.recorded?.energyKcal)
                      : "Chưa ghi nhận"}
                  </td>
                  <td>
                    {day.itemCount
                      ? `${day.recordedCount}/${day.itemCount}`
                      : "Chưa có thực đơn"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </section>
  );
}

function IntakeChart({ days }: { days: DaySummary[] }) {
  const maximum = Math.max(
    1000,
    ...days.flatMap((day) => [
      day.target || 0,
      day.planned?.energyKcal || 0,
      day.recorded?.energyKcal || 0,
    ]),
  );
  const ceiling = Math.ceil(maximum / 500) * 500;
  return (
    <>
      <div className="trend-legend">
        <span>
          <i className="legend-planned" />
          Theo thực đơn
        </span>
        <span>
          <i className="legend-recorded" />
          Đã ghi nhận
        </span>
        <span>
          <i className="legend-target" />
          Mục tiêu
        </span>
      </div>
      {days.some((day) => day.plan) ? (
        <div className="intake-chart-scroll">
          <div
            className="intake-chart"
            style={{ minWidth: Math.max(500, days.length * 56) }}
            role="img"
            aria-label={`Biểu đồ năng lượng ${days.length} ngày. Có bảng số liệu chi tiết bên dưới. Ngày chưa ghi nhận không được tính là 0 kcal.`}
          >
            <div className="intake-axis" aria-hidden="true">
              {[ceiling, ceiling * 0.75, ceiling * 0.5, ceiling * 0.25, 0].map(
                (value) => (
                  <span key={value}>{number(value)}</span>
                ),
              )}
            </div>
            <div className="intake-columns">
              {days.map((day) => (
                <div
                  key={day.date}
                  className={`intake-column${!day.plan ? " no-plan" : ""}`}
                >
                  <div
                    className="intake-bars"
                    title={`${dateLabel(day.date)}: theo thực đơn ${number(day.planned?.energyKcal)} kcal; đã ghi nhận ${day.recordedCount ? `${number(day.recorded?.energyKcal)} kcal` : "chưa có"}; mục tiêu ${number(day.target)} kcal.`}
                  >
                    {day.planned?.energyKcal != null && (
                      <span
                        className="intake-bar planned"
                        style={{
                          height: `${(day.planned.energyKcal / ceiling) * 100}%`,
                        }}
                      />
                    )}
                    {day.recorded?.energyKcal != null && (
                      <span
                        className="intake-bar recorded"
                        style={{
                          height: `${(day.recorded.energyKcal / ceiling) * 100}%`,
                        }}
                      />
                    )}
                    {day.target !== null && (
                      <span
                        className="intake-target"
                        style={{ bottom: `${(day.target / ceiling) * 100}%` }}
                      />
                    )}
                    {!day.plan && <span className="intake-missing">—</span>}
                  </div>
                  <span className="intake-date">
                    {dateLabel(day.date, { day: "numeric", month: "numeric" })}
                  </span>
                  <span className="intake-log-status">
                    {day.recordedCount
                      ? `${day.recordedCount}/${day.itemCount} món`
                      : day.plan
                        ? "Chưa ghi"
                        : "Chưa có"}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>
      ) : (
        <Empty title="Biểu đồ bắt đầu từ thực đơn đầu tiên">
          Tạo thực đơn và ghi nhận món đã ăn. Dữ liệu của bạn sẽ xuất hiện tại
          đây.
        </Empty>
      )}
    </>
  );
}
