import { API_MODE } from "../../../lib/config";
import { activityService } from "../services/activityService";
import { useState, type FormEvent } from "react";

import { Link } from "react-router-dom";

import { ArrowRight, Plus, Trash2 } from "lucide-react";

import { Alert, Button, Confirm, Empty, IconButton, Modal, PageTitle } from "../../../components/common/ui";

import { useApp } from "../../../store/UserDataProvider";



import { addDays, dateLabel, localDate, number, uid } from "../../../utils";

import type { CheckIn } from "../../../types";
import { TableCell } from "../../../components/common/TableCell";

export function ProgressPage() {
  const { userData: w, update, toast } = useApp();
  const [open, setOpen] = useState(false);
  const [remove, setRemove] = useState<CheckIn | null>(null);
  const [range, setRange] = useState(30);
  const cutoff = addDays(localDate(), -range);
  const entries = w.checkIns
    .filter((c) => c.date >= cutoff)
    .sort((a, b) => a.date.localeCompare(b.date));
  const latest = entries.at(-1);
  const change = entries.length > 1 ? latest!.weight - entries[0].weight : null;
  async function deleteEntry() {
    if (!remove) return;
    if (API_MODE === "api") await activityService.deleteCheckIn(remove.id);
    update((s) => ({
      ...s,
      checkIns: s.checkIns.filter((c) => c.id !== remove.id),
    }));
    toast("Đã xóa bản ghi.");
  }
  return (
    <div className="stack-lg">
      <PageTitle
        eyebrow="GHI NHẬN TỪNG THAY ĐỔI"
        title="Theo dõi sức khỏe"
        description="Lưu các chỉ số bạn tự ghi nhận, để hiểu hơn hành trình của mình."
        actions={
          <Button onClick={() => setOpen(true)}>
            <Plus size={16} />
            Thêm ghi nhận
          </Button>
        }
      />
      <div className="progress-stats">
        <div className="panel">
          <span>Cân nặng gần nhất</span>
          <strong>
            {latest ? number(latest.weight, 1) : "—"}
            <small> kg</small>
          </strong>
          <p>
            {latest
              ? dateLabel(latest.date)
              : "Chưa có ghi nhận trong khoảng này"}
          </p>
        </div>
        <div className="panel">
          <span>Thay đổi trong {range} ngày</span>
          <strong>
            {change === null
              ? "—"
              : `${change > 0 ? "+" : ""}${number(change, 1)}`}
            <small> kg</small>
          </strong>
          <p>
            {entries.length > 1
              ? "So với lần ghi đầu trong khoảng"
              : "Cần ít nhất hai lần ghi nhận"}
          </p>
        </div>
        <div className="panel">
          <span>Mục tiêu từ hồ sơ</span>
          <strong>
            {number(w.profile.targetWeight, 1)}
            <small> kg</small>
          </strong>
          <Link className="text-link" to="/ho-so">
            Điều chỉnh hồ sơ
            <ArrowRight size={14} />
          </Link>
        </div>
      </div>
      <section className="panel chart-panel">
        <div className="section-title">
          <div>
            <h2>Cân nặng theo thời gian</h2>
            <p>Chỉ hiển thị số liệu bạn đã nhập.</p>
          </div>
          <select
            aria-label="Khoảng thời gian"
            value={range}
            onChange={(e) => setRange(Number(e.target.value))}
          >
            <option value={7}>7 ngày</option>
            <option value={30}>30 ngày</option>
            <option value={90}>90 ngày</option>
            <option value={365}>1 năm</option>
          </select>
        </div>
        {entries.length ? (
          <WeightChart entries={entries} />
        ) : (
          <Empty
            title="Bắt đầu từ một lần ghi nhận"
            action={
              <Button variant="secondary" onClick={() => setOpen(true)}>
                <Plus size={16} />
                Ghi nhận cân nặng
              </Button>
            }
          >
            Biểu đồ sẽ xuất hiện sau khi bạn thêm số đo đầu tiên.
          </Empty>
        )}
      </section>
      <section className="stack">
        <h2>Nhật ký ghi nhận</h2>
        {entries.length ? (
          <div className="panel table-wrap">
            <table role="table" aria-label="Nhật ký ghi nhận sức khỏe">
              <thead role="rowgroup">
                <tr role="row">
                  <th role="columnheader" scope="col">
                    Ngày
                  </th>
                  <th role="columnheader" scope="col">
                    Cân nặng
                  </th>
                  <th role="columnheader" scope="col">
                    Đường huyết tự nhập
                  </th>
                  <th role="columnheader" scope="col">
                    Ghi chú
                  </th>
                  <th role="columnheader" scope="col">
                    <span className="sr-only">Thao tác</span>
                  </th>
                </tr>
              </thead>
              <tbody role="rowgroup">
                {[...entries].reverse().map((c) => (
                  <tr key={c.id} role="row">
                    <TableCell label="Ngày">
                      {dateLabel(c.date, {
                        day: "numeric",
                        month: "numeric",
                        year: "numeric",
                      })}
                    </TableCell>
                    <TableCell label="Cân nặng">
                      {number(c.weight, 1)} kg
                    </TableCell>
                    <TableCell label="Đường huyết tự nhập">
                      {c.glucose === undefined
                        ? "—"
                        : `${number(c.glucose, 1)} mmol/L`}
                    </TableCell>
                    <TableCell label="Ghi chú">{c.note || "—"}</TableCell>
                    <TableCell label="Thao tác">
                      <IconButton
                        label={`Xóa ghi nhận ${c.date}`}
                        onClick={() => setRemove(c)}
                      >
                        <Trash2 size={15} />
                      </IconButton>
                    </TableCell>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="muted">Chưa có bản ghi trong khoảng thời gian này.</p>
        )}
      </section>
      <Alert>
        Các chỉ số do bạn tự nhập, chưa được thiết bị hoặc chuyên gia xác minh.
        Trang này không phân loại hay diễn giải kết quả xét nghiệm.
      </Alert>
      {open && <CheckInModal onClose={() => setOpen(false)} />}
      {remove && (
        <Confirm
          title="Xóa bản ghi này?"
          description="Bản ghi đã chọn sẽ bị xóa khỏi nhật ký sức khỏe."
          onConfirm={deleteEntry}
          onClose={() => setRemove(null)}
        />
      )}
    </div>
  );
}
function WeightChart({ entries }: { entries: CheckIn[] }) {
  const weights = entries.map((e) => e.weight),
    min = Math.floor(Math.min(...weights) - 1),
    max = Math.ceil(Math.max(...weights) + 1);
  const timestamps = entries.map((e) =>
      new Date(e.date + "T12:00:00").valueOf(),
    ),
    start = timestamps[0],
    span = timestamps.at(-1)! - start || 1;
  const points = entries.map((entry, i) => ({
    x: entries.length === 1 ? 50 : 2 + ((timestamps[i] - start) / span) * 96,
    y: 100 - ((entry.weight - min) / (max - min)) * 100,
    entry,
  }));
  const ticks = Array.from(
    { length: 5 },
    (_, i) => max - ((max - min) / 4) * i,
  );
  const chartDate = (entry: CheckIn) =>
    dateLabel(entry.date, { day: "numeric", month: "numeric" });
  return (
    <div
      className="weight-chart"
      role="img"
      aria-label={`Biểu đồ cân nặng, ${entries.length} bản ghi. Giá trị đầu ${number(weights[0], 1)} kg, giá trị cuối ${number(weights.at(-1)!, 1)} kg. Xem từng số đo trong nhật ký.`}
    >
      <div className="weight-axis" aria-hidden="true">
        {ticks.map((tick) => (
          <span key={tick}>{number(tick, 2)} kg</span>
        ))}
      </div>
      <div className="weight-plot" aria-hidden="true">
        <svg viewBox="0 0 100 100" preserveAspectRatio="none">
          {[0, 25, 50, 75, 100].map((y) => (
            <line
              key={y}
              x1="0"
              x2="100"
              y1={y}
              y2={y}
              stroke="var(--border-subtle)"
              strokeDasharray="4 4"
              vectorEffect="non-scaling-stroke"
            />
          ))}
          <polyline
            points={points.map((p) => `${p.x},${p.y}`).join(" ")}
            fill="none"
            stroke="var(--brand)"
            strokeWidth="3"
            strokeLinejoin="round"
            vectorEffect="non-scaling-stroke"
          />
        </svg>
        {points.map((point) => (
          <span
            className="weight-point"
            key={point.entry.id}
            style={{ left: `${point.x}%`, top: `${point.y}%` }}
            title={`${chartDate(point.entry)} · ${number(point.entry.weight, 1)} kg`}
          />
        ))}
      </div>
      <div className="weight-dates" aria-hidden="true">
        <span>{chartDate(entries[0])}</span>
        {entries[0].date !== entries.at(-1)!.date && (
          <span>{chartDate(entries.at(-1)!)}</span>
        )}
      </div>
    </div>
  );
}
function CheckInModal({ onClose }: { onClose: () => void }) {
  const { userData: w, update, toast } = useApp();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function save(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError("");
    const form = new FormData(e.currentTarget);
    try {
      const date = String(form.get("date")),
        weight = Number(form.get("weight")),
        value = String(form.get("glucose"));
      if (!date || date > localDate() || weight < 20 || weight > 400)
        throw new Error("Ngày ghi nhận hoặc cân nặng không hợp lệ.");
      if (value && (Number(value) < 0.1 || Number(value) > 60))
        throw new Error("Kiểm tra lại giá trị và đơn vị đường huyết mmol/L.");
      let next: CheckIn = {
        id: uid(),
        date,
        weight,
        ...(value ? { glucose: Number(value) } : {}),
        note: String(form.get("note") || "").trim(),
      };
      if (API_MODE === "api") next = await activityService.checkIn(next);
      update((s) => ({ ...s, checkIns: [...s.checkIns, next] }));
      toast("Đã thêm ghi nhận sức khỏe.");
      onClose();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal title="Thêm ghi nhận sức khỏe" onClose={onClose}>
      <form className="stack" onSubmit={save}>
        <label>
          Ngày ghi nhận
          <input
            name="date"
            type="date"
            required
            max={localDate()}
            defaultValue={localDate()}
          />
        </label>
        <label>
          Cân nặng (kg)
          <input
            name="weight"
            type="number"
            required
            min={20}
            max={400}
            step={0.1}
            defaultValue={w.profile.weight}
          />
        </label>
        <label>
          Đường huyết (mmol/L) · không bắt buộc
          <input
            name="glucose"
            type="number"
            min={0.1}
            max={60}
            step={0.1}
            placeholder="Nhập số đo nếu có"
          />
        </label>
        <label>
          Ghi chú
          <textarea
            name="note"
            maxLength={500}
            rows={3}
            placeholder="Ví dụ: đo buổi sáng, trước bữa ăn…"
          />
        </label>
        {error && <Alert error>{error}</Alert>}
        <div className="modal-actions">
          <Button variant="secondary" type="button" onClick={onClose}>
            Hủy
          </Button>
          <Button busy={busy} type="submit">
            Lưu ghi nhận
          </Button>
        </div>
      </form>
    </Modal>
  );
}
