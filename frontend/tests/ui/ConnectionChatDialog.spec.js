import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { createApp, h, nextTick } from "vue";
import ConnectionChatDialog from "../../src/components/ConnectionChatDialog.vue";
import { api } from "../../src/services/api";

const { clients } = vi.hoisted(() => ({ clients: [] }));

vi.mock("../../src/services/api", () => ({ api: vi.fn() }));
vi.mock("../../src/composables/useAuth", async () => {
  const { ref } = await import("vue");
  return {
    token: ref("chat-test-token"),
    user: ref({ userId: "student-a", name: "Alex Tan" }),
  };
});
vi.mock("@stomp/stompjs", () => ({
  Client: class {
    constructor(configuration) {
      this.configuration = configuration;
      this.published = [];
      clients.push(this);
    }

    activate() {
      queueMicrotask(() => this.configuration.onConnect());
    }

    subscribe(destination, callback) {
      this.destination = destination;
      this.onMessage = callback;
      return { unsubscribe: vi.fn() };
    }

    publish(message) {
      this.published.push(message);
    }

    async deactivate() {
      this.deactivated = true;
    }
  },
}));

let app;

HTMLDialogElement.prototype.showModal = function () {
  this.open = true;
};
HTMLDialogElement.prototype.close = function () {
  this.open = false;
};

async function settle() {
  await new Promise((resolve) => setTimeout(resolve, 0));
  await nextTick();
}

function mountChat() {
  app = createApp({
    render: () =>
      h(ConnectionChatDialog, {
        connection: {
          connectionId: "connection-1",
          partnerName: "Study buddy",
        },
      }),
  });
  app.mount(document.body);
}

beforeEach(() => {
  clients.length = 0;
  api.mockReset().mockResolvedValue([]);
  document.body.innerHTML = "";
});

afterEach(() => {
  app?.unmount();
  app = null;
  document.body.innerHTML = "";
});

test("opens an authenticated live conversation and loads persisted history", async () => {
  api.mockResolvedValue([
    {
      messageId: "saved-message",
      connectionId: "connection-1",
      senderId: "student-b",
      content: "Previously sent",
      sentAt: "2026-10-25T10:00:00Z",
    },
  ]);
  mountChat();
  await settle();

  expect(clients[0].configuration.connectHeaders.Authorization).toBe(
    "Bearer chat-test-token",
  );
  expect(clients[0].destination).toBe("/topic/connections/connection-1");
  expect(api).toHaveBeenCalledWith(
    "/api/connections/connection-1/messages",
  );
  expect(document.body.textContent).toContain("Previously sent");
  expect(
    document.querySelector('[role="img"][aria-label="Study buddy"]').textContent,
  ).toBe("SB");
});

test("sends messages over the live connection and displays received messages", async () => {
  mountChat();
  await settle();

  const textarea = document.querySelector("textarea");
  textarea.value = "Hello there";
  textarea.dispatchEvent(new Event("input", { bubbles: true }));
  await nextTick();
  document.querySelector("form").dispatchEvent(
    new Event("submit", { bubbles: true, cancelable: true }),
  );
  await settle();

  expect(clients[0].published[0].destination).toBe(
    "/app/connections/connection-1/send",
  );
  expect(JSON.parse(clients[0].published[0].body)).toEqual({
    content: "Hello there",
  });

  clients[0].onMessage({
    body: JSON.stringify({
      messageId: "own-message",
      connectionId: "connection-1",
      senderId: "student-a",
      content: "Hello there",
      sentAt: "2026-10-25T10:00:30Z",
    }),
  });
  clients[0].onMessage({
    body: JSON.stringify({
      messageId: "live-message",
      connectionId: "connection-1",
      senderId: "student-b",
      content: "Hello back",
      sentAt: "2026-10-25T10:01:00Z",
    }),
  });
  await settle();
  expect(document.body.textContent).toContain("Hello back");
  expect(
    document.querySelector('[role="img"][aria-label="Alex Tan"]').textContent,
  ).toBe("AT");
  expect(
    document.querySelector('[role="img"][aria-label="Study buddy"]').textContent,
  ).toBe("SB");
});

test("shows history errors and closes its WebSocket on unmount", async () => {
  api.mockRejectedValue(new Error("Chat history unavailable"));
  mountChat();
  await settle();

  expect(document.querySelector('[role="alert"]').textContent).toBe(
    "Chat history unavailable",
  );
  const activeClient = clients[0];
  app.unmount();
  app = null;
  await settle();
  expect(activeClient.deactivated).toBe(true);
});
