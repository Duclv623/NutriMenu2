import type { User } from "../../../types";
import { uid } from "../../../utils";
import { API_MODE } from "../../../lib/config";
import { json, request } from "../../../lib/api";

type Account = { user: User; salt: string; hash: string };
async function hashPassword(password: string, salt: string) {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(password),
    "PBKDF2",
    false,
    ["deriveBits"],
  );
  const bits = await crypto.subtle.deriveBits(
    {
      name: "PBKDF2",
      salt: new TextEncoder().encode(salt),
      iterations: 100000,
      hash: "SHA-256",
    },
    key,
    256,
  );
  return Array.from(new Uint8Array(bits))
    .map((n) => n.toString(16).padStart(2, "0"))
    .join("");
}
export async function authenticate(
  name: string,
  email: string,
  password: string,
  register: boolean,
): Promise<User> {
  if (API_MODE === "api") {
    const result = register
      ? await registerAccount(name, email, password)
      : await loginAccount(email, password);
    if (
      !result.user?.id ||
      result.user.demo ||
      result.accessToken === "nutriguide-session-jwt-token"
    )
      throw new Error(
        "Máy chủ chưa cung cấp phiên đăng nhập thật. Hãy kiểm tra cấu hình kết nối.",
      );
    sessionStorage.removeItem("nutriguide.token");
    if (result.accessToken)
      sessionStorage.setItem("nutriguide.token", result.accessToken);
    return result.user;
  }
  const accounts: Account[] = JSON.parse(
    localStorage.getItem("nutriguide.demo.accounts") || "[]",
  );
  const normalized = email.trim().toLowerCase();
  const existing = accounts.find((a) => a.user.email === normalized);
  if (register) {
    if (existing)
      throw new Error("Email này đã được đăng ký trên trình duyệt.");
    const salt = uid();
    const user = {
      id: uid(),
      name: name.trim(),
      email: normalized,
      demo: true,
    };
    accounts.push({ user, salt, hash: await hashPassword(password, salt) });
    localStorage.setItem("nutriguide.demo.accounts", JSON.stringify(accounts));
    return user;
  }
  if (
    !existing ||
    existing.hash !== (await hashPassword(password, existing.salt))
  )
    throw new Error("Email hoặc mật khẩu chưa đúng.");
  return existing.user;
}
export const demoUser: User = {
  id: "demo",
  name: "Minh Anh",
  email: "demo@nutriguide.local",
  demo: true,
};

async function authRequest<T>(path: string, body: unknown): Promise<T> {
  sessionStorage.removeItem("nutriguide.token");
  const csrf = await request<{ headerName: string; token: string }>("/auth/csrf");
  return request<T>(path, {
    method: "POST", headers: { [csrf.headerName]: csrf.token }, body: json(body),
  });
}

async function loginAccount(email: string, password: string) {
  const result = await authRequest<{ data: {
    accessToken: string; user: { id: string; fullName: string; email: string; role?: string };
  } }>("/auth/login", { email, password });
  return {
    accessToken: result.data.accessToken,
    user: { id: result.data.user.id, role: result.data.user.role, name: result.data.user.fullName, email: result.data.user.email, demo: false },
  };
}

export async function logoutAccount() {
  await request<void>("/auth/logout", { method: "POST" });
}

async function registerAccount(name: string, email: string, password: string) {
  await authRequest("/auth/register", { fullName: name, email, password });
  return loginAccount(email, password);
}
