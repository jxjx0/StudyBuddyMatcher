import router from "../router";
import { token, clearSession, authError } from "../composables/useAuth";

export async function api(path, options = {}) {
  const session = token.value;
  const headers = { ...options.headers };
  if (options.body) headers["Content-Type"] = "application/json";
  if (session) headers.Authorization = `Bearer ${session}`;
  const response = await fetch(path, { ...options, headers });
  const body =
    response.status === 204 ? null : await response.json().catch(() => null);

  // A late response must never show the previous student's data after an account switch.
  if (session !== token.value)
    throw new DOMException("Session changed", "AbortError");
  if (response.status === 401 && session) {
    clearSession();
    authError.value = "Your session has expired. Please log in again.";
    router.push("/login");
    throw new Error(authError.value);
  }
  if (!response.ok)
    throw new Error(
      body?.message || `Request failed (${response.status}). Please try again.`,
    );
  if (
    (!options.method || options.method === "GET") &&
    response.status !== 204 &&
    body === null
  )
    throw new Error("The server is not responding. Please try again.");
  return body;
}
