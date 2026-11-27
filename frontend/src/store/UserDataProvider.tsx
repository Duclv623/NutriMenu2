import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import type { User, UserData } from "../types";
import { createDemoData } from "../data/seed";
import { useLocation } from "react-router-dom";
import { API_MODE } from "../lib/config";
import { logoutAccount } from "../features/auth/services/authService";
import { loadPageData } from "./loadPageData";
import { createUserDataCache, emptyUserData, isPageDataReady, updateCachedUserData, type UserDataCache } from "./userDataCache";
export { emptyUserData } from "./userDataCache";
type Context = {
  user: User | null;
  userData: UserData;
  loading: boolean;
  hasData: boolean;
  loadError: string;
  storageError: string;
  login: (user: User) => void;
  logout: () => Promise<void>;
  reload: (background?: boolean) => Promise<void>;
  update: (fn: (state: UserData) => UserData) => void;
  toast: (message: string) => void;
  notice: string;
};
const AppContext = createContext<Context | null>(null);
function getUser(): User | null {
  try {
    return JSON.parse(sessionStorage.getItem("nutriguide.user") || "null");
  } catch {
    return null;
  }
}
function renameFields(value: object, fields: Record<string, string>) {
  const record = value as Record<string, unknown>;
  for (const [oldKey, newKey] of Object.entries(fields)) {
    if (!Object.hasOwn(record, oldKey)) continue;
    if (!Object.hasOwn(record, newKey)) record[newKey] = record[oldKey];
    delete record[oldKey];
  }
}

export function readSavedWorkspace(saved: string): UserData {
  const workspace = JSON.parse(saved) as UserData;
  if (!workspace?.profile || !Array.isArray(workspace.foods) || !Array.isArray(workspace.plans))
    throw new Error("Dữ liệu lưu trên trình duyệt không hợp lệ.");
  const nutrients = {
    energy: "energyKcal", carbs: "netCarbG", protein: "proteinG",
    fat: "fatG", fiber: "fiberG", sodium: "sodiumMg",
  };
  renameFields(workspace.profile, { activity: "activityLevel" });
  for (const food of workspace.foods) renameFields(food.nutrition, nutrients);
  const planItemFields = { foodId: "dishId", servings: "servingMultiplier" };
  for (const plan of workspace.plans) {
    for (const item of [...plan.items, ...(plan.skippedItems || [])]) {
      renameFields(item, planItemFields);
      if (item.nutrition) renameFields(item.nutrition, nutrients);
    }
  }
  return workspace;
}

export function UserDataProvider({ children }: { children: ReactNode }) {
  const { pathname } = useLocation();
  const page = API_MODE === "api" ? pathname.replace(/\/+$/, "") || "/" : "/";
  const [loadedPage, setLoadedPage] = useState("");
  const [user, setUser] = useState<User | null>(getUser);
  const [userData, setUserData] = useState<UserData>(() =>
    API_MODE === "api" ? emptyUserData() : createDemoData(),
  );
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [storageError, setStorageError] = useState("");
  const [notice, setNotice] = useState("");
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const ownerRef = useRef<string | null>(null);
  const loadTicket = useRef(0);
  const cacheRef = useRef(createUserDataCache(user?.id ?? null, userData));
  const warmedCache = useRef<UserDataCache | null>(null);
  const warmTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const toast = useCallback((message: string) => {
    setNotice(message);
    clearTimeout(timer.current);
    timer.current = setTimeout(() => setNotice(""), 4500);
  }, []);
  const load = useCallback(async (force = false, background = false) => {
    const ticket = ++loadTicket.current;
    const cache = cacheRef.current;
    setLoading(API_MODE === "api" ? !isPageDataReady(page, cache) : ownerRef.current !== user?.id);
    setLoadError("");
    if (!user) {
      setLoading(false);
      return;
    }
    try {
      let next: UserData;
      if (API_MODE === "api") {
        await loadPageData(page, cache, force);
        next = cache.data;
        if (
          !next.profile ||
          !Array.isArray(next.foods) ||
          !Array.isArray(next.plans)
        )
          throw new Error("Dữ liệu backend không đúng hợp đồng tích hợp.");
      } else {
        const saved = localStorage.getItem(
          `nutriguide.workspace.v1.${user.id}`,
        );
        next = saved ? readSavedWorkspace(saved) : createDemoData(user.name);
        if (
          !next.profile ||
          !Array.isArray(next.foods) ||
          !Array.isArray(next.plans)
        )
          throw new Error(
            "Dữ liệu lưu trên trình duyệt không hợp lệ. Bạn có thể xóa dữ liệu trang này để khởi tạo lại.",
          );
      }
      if (ticket !== loadTicket.current || cache !== cacheRef.current) return;
      cache.data = next;
      setUserData(next);
      ownerRef.current = user.id;
      // Warm the remaining small/shared datasets after the first screen has rendered.
      if (API_MODE === "api" && warmedCache.current !== cache) {
        warmedCache.current = cache;
        clearTimeout(warmTimer.current);
        warmTimer.current = setTimeout(() => {
          if (cache !== cacheRef.current) return;
          const paths = ["/thuc-don", "/theo-doi", "/mua-sam"].filter(path => !isPageDataReady(path, cache));
          void Promise.allSettled(paths.map(path => loadPageData(path, cache))).then(() => {
            if (cache === cacheRef.current && ownerRef.current === user.id) setUserData(cache.data);
          });
        }, 150);
      }
    } catch (error) {
      if (background) throw error;
      if (ticket === loadTicket.current) setLoadError((error as Error).message);
    } finally {
      if (ticket === loadTicket.current) {
        setLoadedPage(page);
        setLoading(false);
      }
    }
  }, [user, page]);
  const reload = useCallback((background = false) => load(true, background), [load]);
  useEffect(() => {
    void load();
  }, [load]);
  useEffect(() => () => { clearTimeout(timer.current); clearTimeout(warmTimer.current); }, []);
  useEffect(() => {
    if (
      API_MODE !== "demo" ||
      !user ||
      loading ||
      loadError ||
      ownerRef.current !== user.id
    )
      return;
    try {
      localStorage.setItem(
        `nutriguide.workspace.v1.${user.id}`,
        JSON.stringify(userData),
      );
      setStorageError("");
    } catch {
      setStorageError(
        "Trình duyệt không lưu được dữ liệu. Dữ liệu hiện tại sẽ mất khi tải lại.",
      );
    }
  }, [userData, user, loading, loadError]);
  const login = (next: User) => {
    loadTicket.current++;
    ownerRef.current = null;
    clearTimeout(warmTimer.current);
    const initial = API_MODE === "api" ? emptyUserData() : createDemoData(next.name);
    cacheRef.current = createUserDataCache(next.id, initial);
    setUserData(initial);
    setLoadedPage("");
    setLoadError("");
    sessionStorage.setItem("nutriguide.user", JSON.stringify(next));
    setUser(next);
  };
  const logout = useCallback(async () => {
    try {
      if (API_MODE === "api") await logoutAccount();
    } catch (error) {
      toast((error as Error).message);
      throw error;
    }
    loadTicket.current++;
    ownerRef.current = null;
    clearTimeout(warmTimer.current);
    sessionStorage.removeItem("nutriguide.user");
    sessionStorage.removeItem("nutriguide.token");
    setUser(null);
    const initial = API_MODE === "api" ? emptyUserData() : createDemoData();
    cacheRef.current = createUserDataCache(null, initial);
    setUserData(initial);
  }, [toast]);
  useEffect(() => {
    const unauthorized = () => {
      if (API_MODE === "demo") {
        toast(
          "Dịch vụ thật yêu cầu đăng nhập. Kiểm tra cấu hình kết nối chatbot.",
        );
        return;
      }
      loadTicket.current++;
      ownerRef.current = null;
      clearTimeout(warmTimer.current);
      cacheRef.current = createUserDataCache(null);
      sessionStorage.removeItem("nutriguide.user");
      sessionStorage.removeItem("nutriguide.token");
      setUser(null);
      setUserData(emptyUserData());
      toast("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
    };
    window.addEventListener("nutriguide:unauthorized", unauthorized);
    return () =>
      window.removeEventListener("nutriguide:unauthorized", unauthorized);
  }, [toast]);
  const ready = !!user && cacheRef.current.owner === user.id &&
    (API_MODE === "api" ? isPageDataReady(page, cacheRef.current) : ownerRef.current === user.id);
  return (
    <AppContext.Provider
      value={{
        user,
        userData: ready ? cacheRef.current.data : userData,
        loading: !!user && !ready && (loading || loadedPage !== page),
        hasData: ready,
        loadError,
        storageError,
        login,
        logout,
        reload,
        update: (fn) => {
          const expectedOwner = user?.id;
          const cache = cacheRef.current;
          if (expectedOwner && ownerRef.current === expectedOwner && cache.owner === expectedOwner) {
            const next = fn(cache.data);
            updateCachedUserData(cache, next);
            setUserData(next);
          }
        },
        toast,
        notice,
      }}
    >
      {children}
    </AppContext.Provider>
  );
}
export function useApp() {
  const value = useContext(AppContext);
  if (!value) throw new Error("Missing UserDataProvider");
  return value;
}
