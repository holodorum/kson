import {
  forwardRef,
  StrictMode,
  useEffect,
  useImperativeHandle,
  useRef,
  useState,
  type Ref,
} from "react";
import { createRoot } from "react-dom/client";
import {
  createKsonEditor,
  type KsonEditor,
  type KsonEditorOptions,
} from "@kson/monaco-editor";
import React from "react";

interface KsonEditorViewProps {
  defaultValue?: string;
  onChange?: (value: string) => void;
  onReady?: (handle: KsonEditor) => void;
  lspOptions?: KsonEditorOptions["lspOptions"];
}

const KsonEditorView = forwardRef(function KsonEditorView(
  { defaultValue, onChange, onReady, lspOptions }: KsonEditorViewProps,
  ref: Ref<KsonEditor | null>,
) {
  const containerRef = useRef<HTMLDivElement>(null);
  const handleRef = useRef<KsonEditor | null>(null);
  const [handle, setHandle] = useState<KsonEditor | null>(null);

  // Read latest callbacks without re-running the create-effect.
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;
  const onReadyRef = useRef(onReady);
  onReadyRef.current = onReady;

  // Re-evaluates once the async create resolves; wrapped dispose nulls the
  // internal ref so unmount cleanup won't double-dispose after explicit dispose.
  useImperativeHandle(ref, () => {
    if (!handle) return null;
    return {
      ...handle,
      dispose() {
        handle.dispose();
        handleRef.current = null;
        setHandle(null);
      },
    };
  }, [handle]);

  useEffect(() => {
    // Guards against StrictMode's double-invoke racing the async create.
    let disposed = false;
    let listener: { dispose(): void } | null = null;

    createKsonEditor(containerRef.current!, {
      value: defaultValue,
      lspOptions,
    }).then((created) => {
      if (disposed) {
        created.dispose();
        return;
      }
      handleRef.current = created;
      setHandle(created);
      listener = created.editor.onDidChangeModelContent(() => {
        onChangeRef.current?.(created.editor.getValue());
      });
      onReadyRef.current?.(created);
    });

    return () => {
      disposed = true;
      listener?.dispose();
      handleRef.current?.dispose();
      handleRef.current = null;
    };
  }, []);

  return <div ref={containerRef} style={{ height: "100%" }} />;
});

const SCHEMA = JSON.stringify({
  $schema: "http://json-schema.org/draft-07/schema#",
  type: "object",
  properties: {
    name: { type: "string", description: "Project name" },
    version: { type: "string", description: "Semantic version" },
    tags: { type: "array", items: { type: "string" } },
  },
  required: ["name", "version"],
  additionalProperties: false,
});

const INITIAL_VALUE =
  "{\n  # Try adding an unknown key to see schema validation\n" +
  '  name: "my-project"\n  version: "1.0.0"\n' +
  '  tags: ["demo" "editor"]\n}';

const REPLACEMENT_VALUE = '{\n  name: "updated"\n  version: "2.0.0"\n}';

type LogEntry = {
  label: string;
  value: string;
  cls: "log-value" | "log-label" | "log-event";
};

function App() {
  const editorRef = useRef<KsonEditor | null>(null);
  const [log, setLog] = useState<LogEntry[]>([
    {
      label: ">",
      value: "createKsonEditor(container, options)",
      cls: "log-label",
    },
  ]);
  const [ready, setReady] = useState(false);

  function append(entry: LogEntry) {
    setLog((prev) => [...prev, entry]);
  }

  return (
    <div className="layout">
      <div className="guide">
        <div className="guide-content">
          <h1>React Demo</h1>
          <p className="subtitle">
            Uncontrolled editor with a ref handle &mdash; the React way to wrap
            a non-React widget. Consumes <code> @kson/monaco-editor </code>
            as a third-party package from <code>dist/</code>.
          </p>

          <h2>1. Wrap with forwardRef</h2>
          <p>
            The component creates the editor once and exposes the{" "}
            <code>KsonEditor</code> handle via <code>useImperativeHandle</code>.
            Parents read and write through the ref, never via a{" "}
            <code>value</code> prop.
          </p>
          <pre>
            <code>{`const ref = useRef<KsonEditor | null>(null);

<KsonEditorView
    ref={ref}
    defaultValue={initial}
    onChange={(v) => /* ... */}
/>`}</code>
          </pre>

          <h2>2. Read and write content</h2>
          <pre>
            <code>ref.current?.editor.getValue();</code>
          </pre>
          <button
            className="try-btn"
            disabled={!ready}
            onClick={() => {
              const v = editorRef.current?.editor.getValue() ?? "";
              append({
                label: ">",
                value: "ref.current.editor.getValue()",
                cls: "log-label",
              });
              append({
                label: "=",
                value: `${v.length} chars`,
                cls: "log-value",
              });
            }}
          >
            Run getValue()
          </button>

          <pre>
            <code>ref.current?.editor.setValue('...');</code>
          </pre>
          <button
            className="try-btn"
            disabled={!ready}
            onClick={() => {
              append({
                label: ">",
                value: "ref.current.editor.setValue('...')",
                cls: "log-label",
              });
              editorRef.current?.editor.setValue(REPLACEMENT_VALUE);
              append({ label: "=", value: "done", cls: "log-event" });
            }}
          >
            Run setValue()
          </button>

          <h2>3. Listen for changes</h2>
          <p>
            <code>onChange</code> is a normal prop &mdash; the component reads
            it through a ref so the create-effect can stay <code>[]</code>-deps
            without going stale. Edits appear in the log below.
          </p>

          <h2>4. Clean up</h2>
          <p>
            Disposal happens automatically on unmount. You can also dispose
            explicitly via the ref:
          </p>
          <pre>
            <code>ref.current?.dispose();</code>
          </pre>
          <button
            className="try-btn"
            disabled={!ready}
            onClick={() => {
              append({
                label: ">",
                value: "ref.current.dispose()",
                cls: "log-label",
              });
              editorRef.current?.dispose();
              editorRef.current = null;
              setReady(false);
              append({ label: "=", value: "disposed", cls: "log-event" });
            }}
          >
            Run dispose()
          </button>
        </div>

        <div className="log">
          {log.map((entry, i) => (
            <div key={i}>
              <span className="log-label">{entry.label} </span>
              <span className={entry.cls}>{entry.value}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="editor-pane">
        <KsonEditorView
          ref={editorRef}
          defaultValue={INITIAL_VALUE}
          onReady={() => {
            setReady(true);
            append({ label: "=", value: "editor ready", cls: "log-event" });
          }}
          onChange={(v) =>
            append({
              label: "onChange",
              value: `${v.length} chars`,
              cls: "log-event",
            })
          }
          lspOptions={{
            bundledSchemas: [{ fileExtension: "kson", schemaContent: SCHEMA }],
            enableBundledSchemas: true,
          }}
        />
      </div>
    </div>
  );
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
