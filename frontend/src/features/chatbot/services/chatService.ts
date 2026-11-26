import { json, request } from "../../../lib/api";
import type { Conversation, MealResult } from "../../../types";
import { uid } from "../../../utils";
export type ChatResponse = {
  session_id: string;
  message_id: string;
  answer: string;
  citations: import("../../../types").Citation[];
  warnings: string[];
  history_saved?: boolean;
  meal_edit_cancelled?: boolean;
  meal_result?: MealResult | null;
};
const ragBase = import.meta.env.VITE_RAG_BASE_URL || "/api/chatbot";
export const rag = {
  chat: (
    message: string,
    sessionId?: string,
    saveHistory = false,
    planId?: string,
    planVersion?: number,
    action?: { itemId: string; action: string },
    signal?: AbortSignal,
  ) =>
    request<ChatResponse>(
      "/chat",
      {
        method: "POST",
        signal,
        body: json({
          message,
          session_id: sessionId || null,
          save_history: saveHistory,
          ...(planId
            ? {
                meal_context: {
                  plan_id: planId,
                  plan_version: planVersion,
                  ...(action ? { item_id: action.itemId } : {}),
                },
              }
            : {}),
          ...(action ? { meal_action: action.action } : {}),
        }),
      },
      ragBase,
    ),
  feedback: (id: string, rating: "helpful" | "not_helpful") =>
    request<void>(
      "/feedback",
      { method: "POST", body: json({ message_id: id, rating }) },
      ragBase,
    ),
  deleteSession: (id: string) =>
    request<void>(
      `/sessions/${encodeURIComponent(id)}`,
      { method: "DELETE" },
      ragBase,
    ),
  status: () => request<Record<string, unknown>>("/status", {}, ragBase),
  context: (planId?: string) =>
    request<{
      dataMode: string;
      profile: { weight?: number };
      plan: { id: string; date: string };
    }>(
      `/meal-context${planId ? `?plan_id=${encodeURIComponent(planId)}` : ""}`,
      {},
      ragBase,
    ),
  sessions: () =>
    request<{ id: string; created_at: string; message_count: number }[]>(
      "/sessions",
      {},
      ragBase,
    ),
  history: (id: string) =>
    request<
      {
        id: string;
        question: string;
        response: ChatResponse;
        created_at: string;
      }[]
    >(`/sessions/${encodeURIComponent(id)}`, {}, ragBase),
  applySwap: (proposal: NonNullable<MealResult["proposal"]>) =>
    request<unknown>(
      "/meal-plans/apply-swap",
      {
        method: "POST",
        body: json({
          planId: proposal.planId,
          proposalId: proposal.id,
          baseVersion: proposal.baseVersion,
        }),
      },
      ragBase,
    ),
};

export async function loadConversation(id: string): Promise<Conversation> {
  const turns = await rag.history(id);
  const last = turns.at(-1);
  return {
    id,
    sessionId: id,
    loaded: true,
    title: turns[0]?.question.slice(0, 50) || "Hội thoại đã lưu",
    updatedAt: last?.created_at || "",
    planId: [...turns]
      .reverse()
      .find((turn) => turn.response.meal_result?.context?.plan_id)?.response
      .meal_result?.context?.plan_id,
    messages: turns.flatMap((turn) => [
      { id: `${turn.id}:user`, role: "user" as const, text: turn.question },
      {
        id: turn.id,
        remoteId: turn.id,
        role: "assistant" as const,
        text: turn.response.answer,
        citations: turn.response.citations,
        warnings: turn.response.warnings,
        historySaved: true,
        mealResult: turn.response.meal_result || undefined,
      },
    ]),
  };
}
export function ragDocumentUrl(value?: string) {
  if (!value) return undefined;
  try {
    const url = new URL(value, window.location.origin);
    return url.pathname.startsWith("/api/")
      ? ragBase.replace(/\/$/, "") +
          url.pathname.slice(4) +
          url.search +
          url.hash
      : value;
  } catch {
    return undefined;
  }
}
export function demoReply(
  message: string,
  conversation: Conversation,
): ChatResponse {
  // Không mô phỏng tư vấn y khoa hay bịa nguồn dẫn khi chưa nối RAG.
  return {
    session_id: conversation.id,
    message_id: uid(),
    citations: [],
    warnings: [],
    answer: `Bạn vừa hỏi: “${message}”.\n\nĐây là bản xem trước giao diện hội thoại. Để nhận câu trả lời có dẫn nguồn từ kho tài liệu của nhóm, hãy kết nối dịch vụ chatbot trong phần Cài đặt. Bạn có thể tiếp tục thử nhập câu hỏi, tạo hội thoại và xóa lịch sử tại đây.`,
  };
}
