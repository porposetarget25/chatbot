import React, { useEffect, useMemo, useRef, useState } from "react";
import "./chat.css";

function randomId(prefix = "s") {
  return `${prefix}-${Math.random().toString(16).slice(2)}-${Date.now().toString(16)}`;
}

export default function App() {
  const [apiBase, setApiBase] = useState("http://localhost:8080");
  const [sessionId, setSessionId] = useState(() => {
    const existing = localStorage.getItem("chat_session_id");
    if (existing) return existing;
    const id = randomId("chat");
    localStorage.setItem("chat_session_id", id);
    return id;
  });

  const [system, setSystem] = useState("You are a helpful Java assistant.");
  const [input, setInput] = useState("");
  const [isStreaming, setIsStreaming] = useState(false);

  const [messages, setMessages] = useState(() => ([
    {
      id: randomId("m"),
      role: "assistant",
      content: "Hi. Ask me anything, and I’ll respond in a streaming chat just like ChatGPT.",
      ts: Date.now(),
    }
  ]));

  const abortRef = useRef(null);
  const listRef = useRef(null);

  // NEW: Track whether the server already sent "done"
  // If yes, a stream close is SUCCESS (do not show network error bubble).
  const streamCompletedRef = useRef(false);

  const canSend = useMemo(() => {
    return input.trim().length > 0 && !isStreaming;
  }, [input, isStreaming]);

  // auto scroll to bottom when messages change
  useEffect(() => {
    const el = listRef.current;
    if (!el) return;
    el.scrollTop = el.scrollHeight;
  }, [messages, isStreaming]);

  async function resetChat() {
    // stop ongoing stream
    if (abortRef.current) abortRef.current.abort();
    abortRef.current = null;

    // new session
    const id = randomId("chat");
    setSessionId(id);
    localStorage.setItem("chat_session_id", id);

    // optionally also clear backend memory
    try {
      await fetch(`${apiBase}/api/chat/${encodeURIComponent(sessionId)}`, { method: "DELETE" });
    } catch (_) {}

    setIsStreaming(false);
    setMessages([{
      id: randomId("m"),
      role: "assistant",
      content: "New chat started. What would you like to talk about?",
      ts: Date.now(),
    }]);
  }

  function appendAssistantToken(token) {
    setMessages(prev => {
      // append to last assistant message if it is streaming "current"
      const copy = [...prev];
      const last = copy[copy.length - 1];
      if (last && last.role === "assistant" && last._streaming === true) {
        copy[copy.length - 1] = {
          ...last,
          content: (last.content ?? "") + token,
        };
        return copy;
      }

      // otherwise create a new assistant message
      copy.push({
        id: randomId("m"),
        role: "assistant",
        content: token,
        ts: Date.now(),
        _streaming: true,
      });
      return copy;
    });
  }

  function finalizeAssistantMessage() {
    setMessages(prev => {
      if (!prev.length) return prev;
      const copy = [...prev];
      const last = copy[copy.length - 1];
      if (last && last.role === "assistant") {
        copy[copy.length - 1] = { ...last, _streaming: false };
      }
      return copy;
    });
  }

  async function sendMessage() {
    const text = input.trim();
    if (!text || isStreaming) return;

    // NEW: reset completion flag for this request
    streamCompletedRef.current = false;

    setInput("");
    setIsStreaming(true);

    // add user message
    setMessages(prev => ([
      ...prev,
      { id: randomId("m"), role: "user", content: text, ts: Date.now() }
    ]));

    // create a placeholder assistant message for streaming
    setMessages(prev => ([
      ...prev,
      { id: randomId("m"), role: "assistant", content: "", ts: Date.now(), _streaming: true }
    ]));

    const controller = new AbortController();
    abortRef.current = controller;

    try {
      const res = await fetch(`${apiBase}/api/chat/stream`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Accept": "text/event-stream",
        },
        body: JSON.stringify({ sessionId, system, prompt: text }),
        signal: controller.signal,
      });

      if (!res.ok || !res.body) {
        throw new Error(`HTTP ${res.status}`);
      }

      const reader = res.body.getReader();
      const decoder = new TextDecoder("utf-8");

      // We parse SSE frames by "\n\n"
      let buffer = "";

      while (true) {
        const { done, value } = await reader.read();

        // If the stream ends, break.
        // IMPORTANT: Do not treat this as an error; "done" is normal when server closes SSE.
        if (done) break;

        buffer += decoder.decode(value, { stream: true });

        // process complete SSE events
        let idx;
        while ((idx = buffer.indexOf("\n\n")) >= 0) {
          const rawEvent = buffer.slice(0, idx);
          buffer = buffer.slice(idx + 2);

          // Each SSE event can contain multiple lines: event:, data:, id:, retry:
          let eventName = "message";
          let dataLines = [];

          for (const line of rawEvent.split(/\r?\n/)) {
            const trimmed = line.trim();
            if (!trimmed) continue;

            if (trimmed.startsWith("event:")) {
              eventName = trimmed.slice("event:".length).trim();
            } else if (trimmed.startsWith("data:")) {
              dataLines.push(trimmed.slice("data:".length).trim());
            } else {
              // ignore comments ":" or other fields
            }
          }

          const data = dataLines.join("\n");

          if (eventName === "token") {
            appendAssistantToken(data);

          } else if (eventName === "done") {
            // NEW: mark successful completion so a stream close won't show "network error"
            streamCompletedRef.current = true;

            finalizeAssistantMessage();
            setIsStreaming(false);
            abortRef.current = null;

          } else if (eventName === "message") {
            // Optional full message event (if you add it server-side)
            // You can ignore it or use it to overwrite the last assistant bubble.
            // (Ignoring is fine if token streaming already built the message.)

          } else if (eventName === "status") {
            // ignore

          } else {
            // if your server returns tokens without event name, treat as token
            if (data) appendAssistantToken(data);
          }
        }
      }

    } catch (e) {
      // NEW: If server already completed properly, do NOT show an error bubble.
      if (streamCompletedRef.current) {
        return;
      }

      // Abort is user-initiated stop; do NOT show an error bubble.
      if (e?.name === "AbortError") {
        return;
      }

      const msg = `Streaming failed: ${e?.message ?? "Network error"}`;

      setMessages(prev => ([
        ...prev,
        { id: randomId("m"), role: "assistant", content: msg, ts: Date.now() }
      ]));

    } finally {
      // Always finalize UI state (safe even if already done)
      finalizeAssistantMessage();
      setIsStreaming(false);
      abortRef.current = null;
    }
  }

  function onKeyDown(e) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      if (canSend) sendMessage();
    }
  }

  function stopStreaming() {
    if (abortRef.current) abortRef.current.abort();
  }

  return (
    <div className="page">
      <div className="shell">
        <header className="topbar">
          <div className="brand">
            <div className="logo" aria-hidden="true">AI</div>
            <div className="brandText">
              <div className="title">RAFA GPT</div>
              <div className="subtitle">The best AI chatbot</div>
            </div>
          </div>

          <div className="controls">
            <div className="pill">
              <span className="pillLabel">API</span>
              <input
                className="pillInput"
                value={apiBase}
                onChange={(e) => setApiBase(e.target.value)}
                spellCheck={false}
              />
            </div>

            <button className="btn ghost" onClick={resetChat} title="Start a new chat">
              New chat
            </button>

            {!isStreaming ? null : (
              <button className="btn danger" onClick={stopStreaming} title="Stop generating">
                Stop
              </button>
            )}
          </div>
        </header>

        <main className="main">
          <aside className="side">
            <div className="card">
              <div className="cardTitle">Session</div>
              <div className="mono">{sessionId}</div>
              <div className="hint">This persists in your browser (localStorage).</div>
            </div>

            <div className="card">
              <div className="cardTitle">System prompt</div>
              <textarea
                className="textarea"
                value={system}
                onChange={(e) => setSystem(e.target.value)}
                rows={6}
              />
              <div className="hint">Use this to steer tone/role.</div>
            </div>

            <div className="card subtle">
              <div className="cardTitle">Tips</div>
              <ul className="tips">
                <li>Press <span className="kbd">Enter</span> to send</li>
                <li><span className="kbd">Shift</span>+<span className="kbd">Enter</span> for new line</li>
                <li>Use “New chat” to reset the session</li>
              </ul>
            </div>
          </aside>

          <section className="chat">
            <div className="chatHeader">
              <div className="chatTitle">Conversation</div>
              <div className="status">
                <span className={`dot ${isStreaming ? "live" : ""}`} />
                {isStreaming ? "Generating…" : "Ready"}
              </div>
            </div>

            <div className="msgList" ref={listRef}>
              {messages.map(m => (
                <MessageBubble key={m.id} msg={m} />
              ))}
            </div>

            <div className="composer">
              <div className="composerInner">
                <textarea
                  className="input"
                  placeholder="Type a message…"
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={onKeyDown}
                  rows={1}
                />
                <button className="btn primary" disabled={!canSend} onClick={sendMessage}>
                  Send
                </button>
              </div>
              <div className="composerFoot">
                <span className="muted">
                  Streaming endpoint: <span className="mono">{apiBase}/api/chat/stream</span>
                </span>
              </div>
            </div>
          </section>
        </main>

        <footer className="footer">
          <span className="muted">Built for Spring Boot SSE streaming responses.</span>
        </footer>
      </div>
    </div>
  );
}

function MessageBubble({ msg }) {
  const isUser = msg.role === "user";
  return (
    <div className={`row ${isUser ? "right" : "left"}`}>
      <div className={`bubble ${isUser ? "user" : "assistant"}`}>
        {!isUser ? (
          <div className="avatar" aria-hidden="true">AI</div>
        ) : (
          <div className="avatar userA" aria-hidden="true">You</div>
        )}

        <div className="content">
          <div className="roleLine">
            <span className="role">{isUser ? "You" : "Assistant"}</span>
            {msg._streaming ? <span className="typing">typing…</span> : null}
          </div>
          <div className="text">
            {msg.content || (msg._streaming ? <span className="cursor" /> : "")}
          </div>
        </div>
      </div>
    </div>
  );
}
