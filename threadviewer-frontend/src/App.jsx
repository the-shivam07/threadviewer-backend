
import { Fragment, useEffect, useState } from "react";
import "./App.css";

const API_BASE_URL = "http://localhost:8081/api/v1";

const STATE_COLORS = {
  RUNNABLE: "#4c91c0",
  BLOCKED: "#d96b63",
  WAITING: "#55a69b",
  TIMED_WAITING: "#d99a52",
  NEW: "#8d83bd",
  TERMINATED: "#84909c",
  UNKNOWN: "#b5bdc7",
};

const DAEMON_COLORS = {
  DAEMON: "#4c91c0",
  NON_DAEMON: "#d99a52",
};

async function readResponse(response) {
  const text = await response.text();

  let data;

  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = text;
  }

  if (!response.ok) {
    const message =
      typeof data === "string"
        ? data
        : data?.message || data?.error || data?.detail;

    throw new Error(
      message || `Request failed (${response.status})`
    );
  }

  return data;
}

function getThreadName(thread) {
  return thread?.name || thread?.threadName || "Unnamed thread";
}

function getStackTrace(thread) {
  const trace = thread?.stackTrace;

  if (Array.isArray(trace)) {
    return trace.length > 0
      ? trace.join("\n")
      : "No stack trace available.";
  }

  if (typeof trace === "string" && trace.trim() !== "") {
    return trace;
  }

  return "No stack trace available.";
}

function formatCpuTime(value) {
  if (value == null || !Number.isFinite(Number(value))) {
    return "Not available";
  }

  return `${Number(value).toLocaleString(undefined, {
    maximumFractionDigits: 2,
  })} ms`;
}

function getLockIds(thread) {
  const trace = getStackTrace(thread);
  const waiting = [...trace.matchAll(/(?:waiting to lock|parking to wait for|waiting to re-lock in wait\(\))\s*<(0x[\da-fA-F]+)>/g)].map((match) => match[1].toLowerCase());
  const locked = [...trace.matchAll(/(?:-\s*locked|locked)\s*<(0x[\da-fA-F]+)>/g)].map((match) => match[1].toLowerCase());
  return { waiting: [...new Set(waiting)], locked: [...new Set(locked)] };
}

function normalizeStackTrace(thread) {
  return getStackTrace(thread)
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && line !== "No stack trace available.")
    .join("\n");
}

function RelationshipGraph({ relationships, emptyMessage, onSelectThread }) {
  if (!relationships?.length) return <p className="empty-message">{emptyMessage}</p>;

  const groups = new Map();
  relationships.forEach((relationship) => {
    const key = getThreadName(relationship.from);
    const current = groups.get(key) || { owner: relationship.from, edges: [] };
    current.edges.push(relationship);
    groups.set(key, current);
  });

  const activateThread = (thread) => {
    if (thread && typeof onSelectThread === "function") onSelectThread(thread);
  };

  return (
    <div className="relationship-graphs">
      {[...groups.values()].map(({ owner, edges }, groupIndex) => {
        const height = Math.max(132, edges.length * 40 + 42);
        const rootY = height / 2;
        const ownerLabel = getThreadName(owner);
        const shortOwner = ownerLabel.length > 25 ? `${ownerLabel.slice(0, 22)}…` : ownerLabel;
        return (
          <div className="graph-owner-group" key={`${ownerLabel}-${groupIndex}`}>
            <h4>{ownerLabel} is blocking {edges.length} {edges.length === 1 ? "thread" : "threads"}.</h4>
            <p className="graph-explainer">Select a node to inspect its thread details.</p>
            <svg className="graph-svg" viewBox={`0 0 760 ${height}`} role="img" aria-label={`Lock relationships owned by ${ownerLabel}`}>
              {edges.map(({ to, lockId }, index) => {
                const y = index * 40 + 21;
                const nodeY = y + 10;
                const waiterLabel = getThreadName(to);
                const shortWaiter = waiterLabel.length > 42 ? `${waiterLabel.slice(0, 39)}…` : waiterLabel;
                return (
                  <g key={`${lockId}-${waiterLabel}-${index}`}>
                    <path className="graph-link" d={`M 190 ${rootY} C 265 ${rootY}, 315 ${nodeY}, 410 ${nodeY}`} />
                    <circle
                      className="graph-leaf-node graph-clickable-node"
                      cx="410"
                      cy={nodeY}
                      r="8"
                      role="button"
                      tabIndex="0"
                      aria-label={`View ${waiterLabel}`}
                      onClick={() => activateThread(to)}
                      onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); activateThread(to); } }}
                    >
                      <title>{waiterLabel}</title>
                    </circle>
                    <text
                      className="graph-thread-label graph-clickable-label"
                      x="428"
                      y={nodeY + 4}
                      role="button"
                      tabIndex="0"
                      onClick={() => activateThread(to)}
                      onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); activateThread(to); } }}
                    >
                      {shortWaiter}
                      <title>{`${waiterLabel} — lock ${lockId}`}</title>
                    </text>
                  </g>
                );
              })}
              <circle
                className="graph-root-node graph-clickable-node"
                cx="170"
                cy={rootY}
                r="19"
                role="button"
                tabIndex="0"
                aria-label={`View blocking thread ${ownerLabel}`}
                onClick={() => activateThread(owner)}
                onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); activateThread(owner); } }}
              >
                <title>{ownerLabel}</title>
              </circle>
              <text className="graph-owner-label" x="16" y={rootY - 5}>
                <tspan x="16" dy="0">{shortOwner.length > 18 ? `${shortOwner.slice(0, 17)}…` : shortOwner}</tspan>
                <tspan x="16" dy="17">Blocking thread</tspan>
              </text>
            </svg>
          </div>
        );
      })}
    </div>
  );
}

function App() {
  const [file, setFile] = useState(null);
  const [result, setResult] = useState(null);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [error, setError] = useState("");
  const [historyError, setHistoryError] = useState("");
  const [historyRequested, setHistoryRequested] = useState(false);
  const [selectedThreadType, setSelectedThreadType] =
    useState("DAEMON");
  const [showThreadDetails, setShowThreadDetails] = useState(false);
  const [selectedStackTraceKey, setSelectedStackTraceKey] = useState(null);
  const [selectedState, setSelectedState] = useState(null);
  const [selectedThreadGroup, setSelectedThreadGroup] = useState(null);
const [showAllThreadGroups, setShowAllThreadGroups] = useState(false);


  async function loadHistory() {
    setHistoryRequested(true);
    setHistoryLoading(true);
    setHistoryError("");

    try {
      const response = await fetch(
        `${API_BASE_URL}/thread/history`
      );

      const data = await readResponse(response);

      const records = Array.isArray(data)
        ? data
        : Array.isArray(data?.content)
          ? data.content
          : Array.isArray(data?.data)
            ? data.data
            : [];

      setHistory(records);
    } catch (err) {
      setHistoryError(
        err.message || "Unable to load analysis history."
      );
    } finally {
      setHistoryLoading(false);
    }
  }

  function handleFileChange(event) {
    const selectedFile = event.target.files?.[0];

    setFile(selectedFile || null);
    setError("");
    setResult(null);
  }

  async function handleAnalyze() {
    if (!file) {
      setError("Please select a thread dump file first.");
      return;
    }

    setLoading(true);
    setError("");
    setResult(null);

    try {
      const formData = new FormData();
      formData.append("file", file);

      const response = await fetch(
        `${API_BASE_URL}/thread/upload`,
        {
          method: "POST",
          body: formData,
        }
      );

      const data = await readResponse(response);

      if (!data || typeof data !== "object") {
        throw new Error(
          "Invalid analysis result received from the server."
        );
      }

      setResult(data);
      setSelectedThreadType("DAEMON");
      setSelectedState(null);
    } catch (err) {
      setError(
        `${err.message || "Analysis failed."} Check that the backend is running on port 8081.`
      );
    } finally {
      setLoading(false);
    }
  }




function handleGeneratePdf() {
  if (!result) {
    setError("Analyze a thread dump before generating the PDF.");
    return;
  }

  // Print the real rendered dashboard instead of rebuilding its contents in jsPDF.
  // This preserves the current charts, tables, selected details and stack traces.
  const previousTitle = document.title;
  const fileName = file?.name || "thread-dump";
  document.title = `ThreadViewer-${fileName.replace(/\.[^.]+$/, "")}-report`;
  setError("");

  const restoreTitle = () => {
    document.title = previousTitle;
    window.removeEventListener("afterprint", restoreTitle);
  };

  window.addEventListener("afterprint", restoreTitle);
  window.print();
}


  function renderThread(thread, index) {
    return (
      <article
        className="thread-item compact-thread-card"
        key={`${thread?.id ?? "thread"}-${index}`}
      >
        <div className="thread-item-header">
          <div className="compact-thread-title">
            <h3>{getThreadName(thread)}</h3>
            <p>Thread ID: {thread?.id ?? "N/A"}</p>
          </div>
          <span
            className={`state-badge state-${String(thread?.state ?? "UNKNOWN")
              .toLowerCase()
              .replaceAll("_", "-")}`}
          >
            {String(thread?.state ?? "UNKNOWN").replaceAll("_", " ")}
          </span>
        </div>
        <div className="thread-metadata compact-thread-metadata">
          <span><strong>Priority</strong><b>{thread?.priority ?? "N/A"}</b></span>
          <span><strong>Daemon</strong><b>{String(thread?.daemon ?? thread?.isDaemon ?? "N/A")}</b></span>
          <span><strong>Native ID</strong><b>{thread?.nativeThreadId ?? "N/A"}</b></span>
        </div>
        <details className="stack-trace-details">
          <summary>View Stack Trace</summary>
          <pre>{getStackTrace(thread)}</pre>
        </details>
      </article>
    );
  }

  const allThreads = Array.isArray(result?.allThreads)
    ? result.allThreads
    : [];
    const deadlockedThreadDetails = Array.isArray(result?.deadlockedThreads)
      ? result.deadlockedThreads
          .map((deadlockedName) =>
            allThreads.find(
              (thread) =>
                thread?.name === deadlockedName ||
                thread?.threadName === deadlockedName
            )
          )
          .filter(Boolean)
      : [];

  // Build lock-owner relationships only from lock IDs present in real stack traces.
  const lockOwners = new Map();
  allThreads.forEach((thread) => {
    getLockIds(thread).locked.forEach((lockId) => {
      if (!lockOwners.has(lockId)) lockOwners.set(lockId, thread);
    });
  });
  const lockRelationships = allThreads.flatMap((thread) =>
    getLockIds(thread).waiting.flatMap((lockId) => {
      const owner = lockOwners.get(lockId);
      return owner && owner !== thread
        ? [{ from: owner, to: thread, lockId }]
        : [];
    })
  );
  const blockedThreads = allThreads.filter(
    (thread) => String(thread?.state ?? "").toUpperCase() === "BLOCKED"
  );
  const deadlockedNames = new Set(deadlockedThreadDetails.map(getThreadName));
  const deadlockRelationships = lockRelationships.filter(
    ({ from, to }) => deadlockedNames.has(getThreadName(from)) || deadlockedNames.has(getThreadName(to))
  );
  const identicalStackGroups = (() => {
    const groups = new Map();
    allThreads.forEach((thread) => {
      const normalized = normalizeStackTrace(thread);
      if (!normalized) return;
      const group = groups.get(normalized) || [];
      group.push(thread);
      groups.set(normalized, group);
    });
    return [...groups.entries()]
      .map(([trace, threads]) => ({ trace, threads }))
      .filter((group) => group.threads.length > 1)
      .sort((a, b) => b.threads.length - a.threads.length);
  })();
  const callTreeThreads = allThreads.filter((thread) => normalizeStackTrace(thread)).slice(0, 8);

  const stateCounts = result?.threadStateCounts || {};

  const getStateCount = (state) => {
    const backendValue = stateCounts[state];

    if (backendValue != null) {
      return Number(backendValue) || 0;
    }

    return allThreads.filter(
      (thread) =>
        String(thread?.state ?? "").trim().toUpperCase() === state
    ).length;
  };

  const summaryStates = [
    { key: "WAITING", label: "Waiting" },
    { key: "BLOCKED", label: "Blocked" },
    { key: "RUNNABLE", label: "Runnable" },
    { key: "TIMED_WAITING", label: "Timed Waiting" },
  ];

  const selectedStateThreads = selectedState
    ? allThreads.filter(
        (thread) =>
          String(thread?.state ?? "").trim().toUpperCase() ===
          selectedState
      )
    : [];

const selectedGroupThreads = Array.isArray(
  selectedThreadGroup?.threads
)
  ? selectedThreadGroup.threads
  : [];


  const daemonThreads = allThreads.filter(
    (thread) => thread?.daemon === true || thread?.isDaemon === true
  );

  const nonDaemonThreads = allThreads.filter(
    (thread) => thread?.daemon === false || thread?.isDaemon === false
  );

  const selectedThreads =
    selectedThreadType === "DAEMON"
      ? daemonThreads
      : nonDaemonThreads;

  const daemonCount =
    result?.daemonThreads ?? daemonThreads.length;

  const nonDaemonCount =
    result?.nonDaemonThreads ?? nonDaemonThreads.length;

  const daemonChartTotal = daemonCount + nonDaemonCount;
  const daemonPercentage = daemonChartTotal > 0
    ? (daemonCount / daemonChartTotal) * 100
    : 0;
  const daemonChartStyle = {
    background: daemonChartTotal > 0
      ? `conic-gradient(${DAEMON_COLORS.DAEMON} 0% ${daemonPercentage}%, ${DAEMON_COLORS.NON_DAEMON} ${daemonPercentage}% 100%)`
      : "conic-gradient(#e5e7eb 0% 100%)",
  };

  const totalThreads =
    result?.totalThreads ?? allThreads.length;

  const unknownThreads =
    result?.unknownDaemonThreads ??
    Math.max(0, totalThreads - daemonCount - nonDaemonCount);

  const pieTotal = summaryStates.reduce(
    (total, state) => total + getStateCount(state.key),
    0
  );



  const cpuThreads = Array.isArray(result?.cpuConsumingThreads)
    ? result.cpuConsumingThreads
    : [];

  const gcThreads = Array.isArray(result?.gcConsumingThreads)
    ? result.gcConsumingThreads
    : [];

const methodFrequency =
  result?.methodFrequency &&
  typeof result.methodFrequency === "object"
    ? Object.entries(result.methodFrequency)
        .filter(
          ([method, count]) =>
            method && Number.isFinite(Number(count))
        )
        .sort((a, b) => Number(b[1]) - Number(a[1]))
    : [];
    const [selectedMethod, setSelectedMethod] = useState(null);
    const [showAllMethods, setShowAllMethods] = useState(false);

    const selectedMethodThreads = selectedMethod
      ? allThreads.filter((thread) =>
          getStackTrace(thread)
            .split(/\r?\n/)
            .some((line) => line.includes(selectedMethod))
        )
      : [];
      const getMethodThreadCount = (method) =>
        allThreads.filter((thread) =>
          getStackTrace(thread)
            .split(/\r?\n/)
            .some((line) => line.includes(method))
        ).length;


  // Thread Group Summary
  const threadGroups = Array.isArray(result?.threadGroupSummaries)
    ? result.threadGroupSummaries
    : [];

  const groupTotal = threadGroups.reduce(
    (total, group) => total + (Number(group.threadCount) || 0),
    0
  );

  const groupColors = [
    "#64748b",
    "#2563eb",
    "#0f766e",
    "#d97706",
    "#7c3aed",
  ];

  const groupChartGradient =
    groupTotal > 0
      ? `conic-gradient(${threadGroups
          .map((group, index) => {
            const start = threadGroups
              .slice(0, index)
              .reduce(
                (sum, item) => sum + (Number(item.threadCount) || 0),
                0
              );

            const end = start + (Number(group.threadCount) || 0);

            return `${groupColors[index % groupColors.length]} ${
              (start / groupTotal) * 100
            }% ${(end / groupTotal) * 100}%`;
          })
          .join(", ")})`
      : "conic-gradient(#E5E7EB 0% 100%)";

  return (
    <div className="app">
      <header className="site-header">
        <div className="header-inner">
          <a className="brand" href="#dashboard">
            <span className="brand-mark">TV</span>
            <span className="brand-name">ThreadViewer</span>
          </a>

          <nav className="top-navigation" aria-label="Main navigation">
            <a href="#dashboard">Dashboard</a>
            <a href="#summary">Thread Summary</a>
            <a href="#state-distribution">Thread States</a>
            <a href="#cpu-analysis">CPU Threads</a>
            <a href="#gc-analysis">GC Threads</a>

            <a href="#most-used-methods">Most Used Methods</a>

            <a href="#deadlocks">Deadlocks</a>
            <a href="#history">History</a>
          </nav>
        </div>
      </header>

      <main className="container" id="dashboard">
        <section className="page-heading">
          <div>
            <p className="eyebrow">JAVA PERFORMANCE ANALYSIS</p>
            <h1>Thread Dump Analyzer</h1>
            <p>
              Analyze thread states, CPU time, garbage collection
              activity and potential deadlocks in your Java application.
            </p>
          </div>
        </section>

        <section className="upload-panel" id="upload">
          <div className="section-heading">
            <h2>Upload Thread Dump</h2>
            <p>Select a thread dump file from your computer to begin.</p>
          </div>

          <div className="upload-controls">
            <input
              type="file"
              accept=".txt,.log,.dump,.tdump"
              onChange={handleFileChange}
              disabled={loading}
            />

            <button
              type="button"
              className="primary-button"
              onClick={handleAnalyze}
              disabled={loading || !file}
            >
              {loading ? "Analyzing..." : "Upload and Analyze"}
            </button>
          </div>

          {file && (
            <p className="selected-file">
              <strong>Selected file:</strong> {file.name}
            </p>
          )}

          {error && <p className="error">{error}</p>}

          <p className="note">
            The file is sent to your Spring Boot backend for analysis.
          </p>
        </section>

        {result && (
          <section className="analysis-report">
            <div className="report-heading">
              <div>
                <h2>Thread Dump Analysis Report</h2>
                <p>Summary of the uploaded thread dump.</p>
                <p className="report-file-name"><strong>Thread dump file:</strong> {file?.name || "Not available"}</p>
              </div>

              <div className="report-actions">
                <span className="report-status">Analysis complete</span>

                <button
                  type="button"
                  className="primary-button"
                  onClick={handleGeneratePdf}
                >
                  Print / Save PDF
                </button>
              </div>
            </div>

            <section
              className="report-section summary-section"
              id="summary"
            >
              <div className="section-heading">
                <h2>Thread Count Summary</h2>
                <p>
                  Overview of thread counts and the current thread states.
                </p>
              </div>

              <div className="summary-layout">
                <div className="summary-cards">
                  {summaryStates.map((state) => (
                    <article className="summary-card" key={state.key}>
                      <span className="summary-label">{state.label}</span>
                      <strong>{getStateCount(state.key)}</strong>

                      <button
                        type="button"
                        className="view-details-button"
                        onClick={() => setSelectedState(state.key)}
                      >
                        View Details
                      </button>
                    </article>
                  ))}

                  <article className="summary-card">
                    <span className="summary-label">Total Threads</span>
                    <strong>{totalThreads}</strong>
                  </article>

                  <article className="summary-card">
                    <span className="summary-label">Daemon Threads</span>
                    <strong>{daemonCount}</strong>
                  </article>

                  <article className="summary-card">
                    <span className="summary-label">Non-Daemon Threads</span>
                    <strong>{nonDaemonCount}</strong>
                  </article>

                  <article className="summary-card">
                    <span className="summary-label">Unknown Threads</span>
                    <strong>{unknownThreads}</strong>
                  </article>

                  <article className="summary-card">
                    <span className="summary-label">Deadlock Status</span>
                    <strong>
                      {result?.deadlockDetected ? "Detected" : "Not detected"}
                    </strong>
                  </article>
                </div>

                <div className="state-chart-panel" id="state-distribution">
                  <h3>Thread State Distribution</h3>

                  <div
                    className="state-pie-chart"
                    role="img"
                    aria-label="Pie chart showing thread state distribution"
                    style={{ background: "#ffffff" }}
                  >
                    <svg
                      className="state-pie-slices"
                      viewBox="0 0 200 200"
                      aria-hidden="true"
                    >
                      {(() => {
                        let startAngle = -90;
                        return summaryStates.map((state) => {
                          const count = getStateCount(state.key);
                          if (pieTotal <= 0 || count <= 0) return null;
                          const sweep = (count / pieTotal) * 360;
                          const endAngle = startAngle + sweep;
                          const toPoint = (angle) => ({
                            x: 100 + 100 * Math.cos((angle * Math.PI) / 180),
                            y: 100 + 100 * Math.sin((angle * Math.PI) / 180),
                          });
                          const start = toPoint(startAngle);
                          const end = toPoint(endAngle);
                          const largeArc = sweep > 180 ? 1 : 0;
                          const path = sweep >= 359.999
                            ? "M 100 100 m -100 0 a 100 100 0 1 0 200 0 a 100 100 0 1 0 -200 0"
                            : `M 100 100 L ${start.x} ${start.y} A 100 100 0 ${largeArc} 1 ${end.x} ${end.y} Z`;
                          startAngle = endAngle;
                          return (
                            <path
                              key={state.key}
                              d={path}
                              fill={STATE_COLORS[state.key]}
                              stroke="#ffffff"
                              strokeWidth="1.5"
                            />
                          );
                        });
                      })()}
                    </svg>
                    {summaryStates.map((state) => {
                      const count = getStateCount(state.key);
                      const percentage =
                        pieTotal > 0 ? (count / pieTotal) * 100 : 0;

                      if (percentage === 0) {
                        return null;
                      }

                      const previousCounts = summaryStates
                        .slice(
                          0,
                          summaryStates.findIndex(
                            (item) => item.key === state.key
                          )
                        )
                        .reduce((sum, item) => sum + getStateCount(item.key), 0);

                      const angle =
                        ((previousCounts + count / 2) / pieTotal) * 360 - 90;

                      const radius = 35;
                      const x = 50 + radius * Math.cos((angle * Math.PI) / 180);
                      const y = 50 + radius * Math.sin((angle * Math.PI) / 180);
                      return (
                        <span
                          key={state.key}
                          className="pie-percentage-label"
                          style={{
                            left: `${x}%`,
                            top: `${y}%`,
                          }}
                        >
                          {Math.round(percentage)}%
                        </span>
                      );
                    })}
                  </div>

                  <div className="chart-legend">
                    {summaryStates.map((state) => (
                      <div className="legend-item" key={state.key}>
                        <span
                          className="legend-color"
                          style={{
                            backgroundColor: STATE_COLORS[state.key],
                          }}
                        />
                        <span>{state.label}</span>
                        <strong>{getStateCount(state.key)}</strong>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </section>

           {selectedState && (
             <div
               className="thread-modal-overlay"
               onClick={() => setSelectedState(null)}
             >
               <section
                 className="thread-modal"
                 role="dialog"
                 aria-modal="true"
                 aria-labelledby="thread-modal-title"
                 onClick={(event) => event.stopPropagation()}
               >
                 <div className="thread-modal-header">
                   <div>
                     <h2 id="thread-modal-title">
                       {selectedState.replaceAll("_", " ")} Threads
                     </h2>
                     <p>
                       Threads found: {selectedStateThreads.length}
                     </p>
                   </div>

                   <button
                     type="button"
                     className="modal-close-button"
                     onClick={() => setSelectedState(null)}
                   >
                     &times;
                   </button>
                 </div>

                 <div className="thread-modal-content">
                   {selectedStateThreads.length > 0 ? (
                     selectedStateThreads.map(renderThread)
                   ) : (
                     <p className="empty-message">
                       No threads found in this state.
                     </p>
                   )}
                 </div>
               </section>
             </div>
           )}
       {selectedThreadGroup && (
         <div
           className="thread-modal-overlay"
           onClick={() => setSelectedThreadGroup(null)}
         >
           <section
             className="thread-modal"
             role="dialog"
             aria-modal="true"
             aria-labelledby="group-modal-title"
             onClick={(event) => event.stopPropagation()}
           >
             <div className="thread-modal-header">
               <div>
                 <h2 id="group-modal-title">
                   {selectedThreadGroup.groupName || "Unknown"}
                 </h2>
                 <p>
                   Threads found: {selectedGroupThreads.length}
                 </p>
               </div>

               <button
                 type="button"
                 className="modal-close-button"
                 onClick={() => setSelectedThreadGroup(null)}
               >
                 &times;
               </button>
             </div>

             <div className="thread-modal-content">
               {selectedGroupThreads.length > 0 ? (
                 selectedGroupThreads.map(renderThread)
               ) : (
                 <p className="empty-message">
                   No thread details are available for this group.
                 </p>
               )}
             </div>
           </section>
         </div>
       )}
   {selectedMethod && (
     <div
       className="thread-modal-overlay"
       onClick={() => setSelectedMethod(null)}
     >
       <section
         className="thread-modal"
         role="dialog"
         aria-modal="true"
         aria-labelledby="method-modal-title"
         onClick={(event) => event.stopPropagation()}
       >
         <div className="thread-modal-header">
           <div>
             <h2 id="method-modal-title">
               Related Threads
             </h2>
             <p>
               Method: {selectedMethod}
             </p>
             <p>
               Threads found: {selectedMethodThreads.length}
             </p>
           </div>

           <button
             type="button"
             className="modal-close-button"
             onClick={() => setSelectedMethod(null)}
           >
             &times;
           </button>
         </div>

         <div className="thread-modal-content">
           {selectedMethodThreads.length > 0 ? (
             selectedMethodThreads.map(renderThread)
           ) : (
             <p className="empty-message">
               No threads found containing this method in their stack traces.
             </p>
           )}
         </div>
       </section>
     </div>
   )}



<section className="report-section" id="thread-group-summary">
  <div className="section-heading">
    <h2>Thread Group Summary</h2>
    <p>Thread distribution and state counts by group.</p>
  </div>

  {threadGroups.length > 0 ? (
    <div className="thread-group-layout">
      <div className="thread-group-chart-wrap">
        <div
          className="thread-group-chart"
          role="img"
          aria-label={`Thread group distribution: ${groupTotal} threads`}
          style={{ background: groupChartGradient }}
        >
          <div className="thread-group-chart-hole">
            <strong>{groupTotal}</strong>
            <span>Threads</span>
          </div>
        </div>
      </div>

      <div className="thread-group-table-wrap">
        <table className="thread-group-table">
          <thead>
            <tr>
              <th>Legends</th>
              <th>Thread Group</th>
              <th>Count</th>
              <th>States</th>
            </tr>
          </thead>

          <tbody>
            {(showAllThreadGroups
              ? threadGroups
              : threadGroups.slice(0, 4)
            ).map((group, index) => (

<tr
  key={`${group.groupName || "Unknown"}-${index}`}
  className="thread-group-row"
  onClick={() => setSelectedThreadGroup(group)}
  title="Click to view threads in this group"
>

                <td>
                  <span
                    className="thread-group-color"
                    style={{
                      backgroundColor:
                        groupColors[index % groupColors.length],
                    }}
                  />
                </td>

                <td>
                  {group.groupName || "Unknown"}
                </td>

                <td>
                  {Number(group.threadCount) || 0}
                </td>

                <td className="thread-group-states">
                  <span>
                    A: {Number(group.activeCount) || 0}
                  </span>
                  <span>
                    I: {Number(group.idleCount) || 0}
                  </span>
                  <span>
                    B: {Number(group.blockedCount) || 0}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        <p className="thread-group-state-note">
          A = Active · I = Idle · B = Blocked
        </p>

        {threadGroups.length > 4 && (
          <button
            type="button"
            className="thread-group-show-all"
            onClick={() =>
              setShowAllThreadGroups((previous) => !previous)
            }
          >
            {showAllThreadGroups
              ? "Show fewer thread groups"
              : "Show all thread groups"}
          </button>
        )}
      </div>
    </div>
  ) : (
    <p className="empty-message">
      Thread group data is not available in the backend response.
    </p>
  )}
</section>



            <section className="report-section" id="daemon-analysis">
              <div className="section-heading">
                <h2>Daemon and Non-Daemon Threads</h2>
                <p>Review threads by daemon classification.</p>
              </div>

              <div className="daemon-analysis-layout">
                <div className="daemon-count-cards">
                <div className={`daemon-count-card daemon-count-card--daemon${selectedThreadType === "DAEMON" ? " is-selected" : ""}`}>
                  <span className="daemon-card-icon" aria-hidden="true">⚙</span>
                  <span className="daemon-count-label">Daemon Threads</span>
                  <strong>{daemonCount}</strong>
                  <span className="daemon-card-description">Background service threads</span>
                  <button
                    type="button"
                    className="daemon-view-details-button"
                    onClick={() => {
                      setSelectedThreadType("DAEMON");
                      setShowThreadDetails(true);
                    }}
                  >
                    View Details
                  </button>
                </div>

                <div className={`daemon-count-card daemon-count-card--non-daemon${selectedThreadType === "NON_DAEMON" ? " is-selected" : ""}`}>
                  <span className="daemon-card-icon" aria-hidden="true">⚙</span>
                  <span className="daemon-count-label">Non-Daemon Threads</span>
                  <strong>{nonDaemonCount}</strong>
                  <span className="daemon-card-description">User application threads</span>
                  <button
                    type="button"
                    className="daemon-view-details-button"
                    onClick={() => {
                      setSelectedThreadType("NON_DAEMON");
                      setShowThreadDetails(true);
                    }}
                  >
                    View Details
                  </button>
                </div>
              </div>

              <div className="daemon-chart-panel">
                  <h3>Thread Classification</h3>
                  <div
                    className="daemon-donut-chart"
                    style={daemonChartStyle}
                    role="img"
                    aria-label={`Daemon: ${daemonCount}; Non-Daemon: ${nonDaemonCount}`}
                  >
                    <div className="daemon-donut-hole">
                      <strong>{daemonChartTotal}</strong>
                      <span>Total threads</span>
                    </div>
                  </div>
                  <div className="daemon-chart-legend">
                    <div className="daemon-chart-legend-item">
                      <span className="daemon-chart-legend-dot daemon-dot--daemon" />
                      <span>Daemon</span>
                      <strong>{daemonCount}</strong>
                    </div>
                    <div className="daemon-chart-legend-item">
                      <span className="daemon-chart-legend-dot daemon-dot--non-daemon" />
                      <span>Non-Daemon</span>
                      <strong>{nonDaemonCount}</strong>
                    </div>
                  </div>
                </div>
              </div>

              {showThreadDetails && (
                <div className="daemon-thread-results" aria-live="polite">
                  <div className="daemon-thread-results-heading">
                    <h3>{selectedThreadType === "DAEMON" ? "Daemon Threads" : "Non-Daemon Threads"} ({selectedThreads.length} details)</h3>
                    <button
                      type="button"
                      className="daemon-hide-details-button"
                      onClick={() => setShowThreadDetails(false)}
                    >
                      Hide details
                    </button>
                  </div>
                  {selectedThreads.length > 0 ? (
                    <div className="daemon-details-table-wrap">
                      <table className="daemon-details-table">
                        <thead>
                          <tr>
                            <th>#</th>
                            <th>Thread Name</th>
                            <th>Thread ID</th>
                            <th>State</th>
                            <th>Priority</th>
                            <th>Stack Trace</th>
                          </tr>
                        </thead>
                        <tbody>
                          {selectedThreads.map((thread, index) => {
                            const traceKey = `${selectedThreadType}-${thread?.id ?? index}-${index}`;
                            const traceIsOpen = selectedStackTraceKey === traceKey;
                            return (
                              <Fragment key={traceKey}>
                                <tr>
                                  <td>{index + 1}</td>
                                  <td className="daemon-details-thread-name">{getThreadName(thread)}</td>
                                  <td>{thread?.id ?? "N/A"}</td>
                                  <td>
                                    <span className={`daemon-state-badge state-${String(thread?.state ?? "UNKNOWN").toLowerCase().replaceAll("_", "-")}`}>
                                      {String(thread?.state ?? "UNKNOWN").replaceAll("_", " ")}
                                    </span>
                                  </td>
                                  <td>{thread?.priority ?? "N/A"}</td>
                                  <td>
                                    <button
                                      type="button"
                                      className="daemon-stack-toggle"
                                      onClick={() => setSelectedStackTraceKey(traceIsOpen ? null : traceKey)}
                                    >
                                      {traceIsOpen ? "Hide Stack Trace" : "View Stack Trace"}
                                    </button>
                                  </td>
                                </tr>
                                {traceIsOpen && (
                                  <tr className="daemon-stack-trace-row">
                                    <td colSpan={6}>
                                      <pre className="daemon-stack-trace-content">{getStackTrace(thread) || "No stack trace available for this thread."}</pre>
                                    </td>
                                  </tr>
                                )}
                              </Fragment>
                            );
                          })}
                        </tbody>
                      </table>
                    </div>
                  ) : (
                    <p className="empty-message">
                      No thread details available for this selection.
                    </p>
                  )}
                </div>
              )}
            </section>

            <section className="report-section" id="cpu-analysis">
              <div className="section-heading">
                <h2>CPU Consuming Threads</h2>
                <p>Threads identified by the backend as CPU consumers.</p>
              </div>

            <div className="cpu-table-wrapper">
              <table className="cpu-thread-table">
                <thead>
                  <tr>
                    <th>Thread</th>
                    <th>CPU consuming thread's stacktrace</th>
                  </tr>
                </thead>

                <tbody>
                  {(cpuThreads.length > 0 ? cpuThreads : allThreads).length > 0 ? (
                    (cpuThreads.length > 0 ? cpuThreads : allThreads).map((thread, index) => {
                      const fullStackTrace = getStackTrace(thread);
                      const stackLines = fullStackTrace.split("\n");
                      const stackTracePreview = stackLines.slice(0, 4).join("\n");
                      const nativeId =
                        thread?.nativeThreadId ??
                        thread?.nativeId ??
                        thread?.nid ??
                        "Not available";

                      return (
                        <tr key={`cpu-${thread?.id ?? nativeId ?? index}`}>
                          <td className="cpu-thread-identity">
                            <strong>{getThreadName(thread)}</strong>
                            <p>nativeId: {nativeId}</p>
                          </td>
                          <td className="cpu-stack-cell">
                            <div className="cpu-stack-preview">
                              {thread?.state && (
                                <div>java.lang.Thread.State: {String(thread.state).replaceAll("_", " ")}</div>
                              )}
                              <pre>{stackTracePreview}</pre>
                              {stackLines.length > 4 && (
                                <span className="cpu-stack-ellipsis">...</span>
                              )}
                            </div>
                            <details className="cpu-complete-stacktrace">
                              <summary>See complete stacktrace</summary>
                              <pre className="stack-trace">{fullStackTrace}</pre>
                            </details>
                          </td>
                        </tr>
                      );
                    })
                  ) : (
                    <tr className="cpu-no-data-row">
                      <td colSpan={2}>
                        No thread records were returned by the analysis response.
                      </td>
                    </tr>
                  )}

                  <tr className="cpu-jvm-summary-row">
                    <td>{allThreads.length} JVM threads</td>
                    <td>
                      {allThreads.length > 0 ? (
                        <details className="cpu-complete-stacktrace">
                          <summary>See complete stacktrace</summary>
                          <div className="cpu-all-stacktraces">
                            {allThreads.map((thread, index) => (
                              <div className="cpu-all-stacktrace-item" key={`jvm-stack-${thread?.id ?? index}`}>
                                <strong>{getThreadName(thread)}</strong>
                                <pre className="stack-trace">{getStackTrace(thread)}</pre>
                              </div>
                            ))}
                          </div>
                        </details>
                      ) : (
                        <span className="cpu-stack-unavailable">No thread details were returned.</span>
                      )}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            </section>

            <section className="report-section" id="blocked-transitive-graphs">
              <div className="section-heading">
                <h2>Blocked Thread – Transitive Graph</h2>
                <p>Lock relationships inferred from monitor IDs in the uploaded thread stack traces.</p>
              </div>
              {blockedThreads.length > 0 ? (
                <div className="transitive-graph-grid">
                  {[1, 2].map((graphNumber) => {
                    const relationships = lockRelationships.filter(({ from, to }) =>
                      String(to?.state ?? "").toUpperCase() === "BLOCKED" || String(from?.state ?? "").toUpperCase() === "BLOCKED"
                    ).slice((graphNumber - 1) * 8, graphNumber * 8);
                    return (
                      <article className="transitive-graph-card" key={graphNumber}>
                        <h3>Blocking relationship graph {graphNumber}</h3>
                        <RelationshipGraph relationships={relationships} onSelectThread={(thread) => setSelectedThreadGroup({ groupName: getThreadName(thread), threads: [thread] })} emptyMessage={graphNumber === 1 ? "No matching lock-owner relationship could be inferred from the captured stack traces." : "No additional lock relationships are available for a second graph."} />
                      </article>
                    );
                  })}
                </div>
              ) : (
                <p className="empty-message">No blocked threads were identified in this dump.</p>
              )}
            </section>

            <section className="report-section" id="gc-analysis">
              <div className="section-heading">
                <h2>GC-Related Threads</h2>
                <p>Threads identified by the backend as GC-related.</p>
              </div>

              {gcThreads.length > 0 ? (
                <article className="gc-summary-card">
                  <div className="gc-summary-icon" aria-hidden="true">GC</div>
                  <strong className="gc-summary-count">{gcThreads.length}</strong>
                  <span className="gc-summary-label">GC threads</span>
                  <button
                    type="button"
                    className="gc-view-details-button"
                    onClick={() => setSelectedThreadGroup({ groupName: "GC Consuming Threads", threads: gcThreads })}
                  >
                    View Details
                  </button>
                </article>
              ) : (
                <p className="empty-message">
                  No GC-related threads were identified.
                </p>
              )}
            </section>



<section className="report-section" id="most-used-methods">
  <div className="section-heading">
    <h2>Most Used Methods</h2>
    <p>
      Methods found most frequently in captured thread stack traces.
      These counts are not actual runtime execution counts.
    </p>
  </div>

  {methodFrequency.length > 0 ? (
    <>
      <div className="method-table-wrapper">
        <table className="method-frequency-table">
          <thead>
            <tr>
              <th>Thread Count</th>
              <th>Method</th>
              <th>Percentage</th>
            </tr>
          </thead>

          <tbody>
            {(showAllMethods
              ? methodFrequency
              : methodFrequency.slice(0, 5)
            ).map(([method, count]) => {
             const totalThreads = allThreads.length;
             const methodThreadCount = getMethodThreadCount(method);

             const percentage = totalThreads > 0
               ? (methodThreadCount / totalThreads) * 100
               : 0;

              return (
                <tr key={method}>
                <td>{methodThreadCount} threads</td>

                  <td>
                    <div className="method-table-name">
                      {method}
                    </div>
                    <button
                      type="button"
                      className="method-stack-link"
                      onClick={() => setSelectedMethod(method)}
                    >
                      click here
                    </button>
                    <span className="method-stack-hint">
                      {" "}to see stack trace
                    </span>
                  </td>

                  <td>
                    <div className="method-percentage">
                      <span>{percentage.toFixed(1)}%</span>
                      <div className="method-percentage-track">
                        <div
                          className="method-percentage-fill"
                          style={{
                            width: `${Math.min(percentage, 100)}%`
                          }}
                        />
                      </div>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {methodFrequency.length > 5 && (
        <button
          type="button"
          className="show-all-methods-button"
          onClick={() => setShowAllMethods((previous) => !previous)}
        >
          {showAllMethods ? "Show fewer methods <<" : "Show all methods >>"}
        </button>
      )}
    </>
  ) : (
    <p className="empty-message">
      No method frequency data is available for this thread dump.
    </p>
  )}
</section>


            <section className="report-section" id="deadlocks">
              <div className="section-heading">
                <h2>Deadlock Analysis</h2>
                <p>Deadlock detection result returned by the backend.</p>
              </div>

              <article className={`deadlock-status-banner ${result?.deadlockDetected ? "is-detected" : "is-clear"}`}>
                <div className="deadlock-status-icon" aria-hidden="true">{result?.deadlockDetected ? "!" : "✓"}</div>
                <div>
                  <span className="summary-label">Deadlock Status</span>
                  <strong>{result?.deadlockDetected ? "Detected" : "Not detected"}</strong>
                  <p>
                    {result?.deadlockDetected
                      ? `${deadlockedThreadDetails.length} identified thread${deadlockedThreadDetails.length === 1 ? " is" : "s are"} involved in the reported deadlock.`
                      : "No deadlock was reported for this thread dump."}
                  </p>
                </div>
              </article>

              {deadlockedThreadDetails.length > 0 ? (
                <div className="deadlock-thread-list">
                  {deadlockedThreadDetails.map((thread, index) => {
                    const ownerRelationship = lockRelationships.find(
                      ({ to }) => getThreadName(to) === getThreadName(thread)
                    );
                    return (
                      <article className="deadlock-thread-card" key={`${thread?.id ?? "deadlocked"}-${index}`}>
                        <div className="deadlock-thread-details">
                          <div className="deadlock-thread-title-row">
                            <h3>{getThreadName(thread)}</h3>
                            <span className={`state-badge state-${String(thread?.state ?? "UNKNOWN").toLowerCase().replaceAll("_", "-")}`}>
                              {String(thread?.state ?? "UNKNOWN").replaceAll("_", " ")}
                            </span>
                          </div>
                          <dl className="deadlock-metadata-list">
                            <div><dt>Thread ID</dt><dd>{thread?.id ?? "N/A"}</dd></div>
                            <div><dt>Priority</dt><dd>{thread?.priority ?? "N/A"}</dd></div>
                            <div><dt>Daemon</dt><dd>{String(thread?.daemon ?? thread?.isDaemon ?? "N/A")}</dd></div>
                            <div><dt>Native ID</dt><dd>{thread?.nativeThreadId ?? "N/A"}</dd></div>
                            <div><dt>State</dt><dd>{String(thread?.state ?? "UNKNOWN").replaceAll("_", " ")}</dd></div>
                            <div><dt>Lock Owner</dt><dd>{ownerRelationship ? `${getThreadName(ownerRelationship.from)} (ID: ${ownerRelationship.from?.id ?? "N/A"})` : "Not available in dump"}</dd></div>
                          </dl>
                        </div>
                        <div className="deadlock-stack-panel">
                          <h4>Stack Trace</h4>
                          <pre>{getStackTrace(thread)}</pre>
                        </div>
                      </article>
                    );
                  })}
                </div>
              ) : result?.deadlockDetected ? (
                <p className="empty-message">
                  Deadlock detected, but detailed thread information is unavailable.
                </p>
              ) : (
                <p className="empty-message">
                  No deadlocked threads detected.
                </p>
              )}
            </section>

            <section className="report-section" id="deadlock-transitive-graphs">
              <div className="section-heading">
                <h2>Dead-Locked Thread – Transitive Graph</h2>
                <p>Relationships are shown only when lock ownership can be matched in the supplied stack traces.</p>
              </div>
              <div className="transitive-graph-grid">
                {[1, 2].map((graphNumber) => {
                  const relationships = deadlockRelationships.slice((graphNumber - 1) * 8, graphNumber * 8);
                  return (
                    <article className="transitive-graph-card" key={graphNumber}>
                      <h3>Blocking relationship graph {graphNumber}</h3>
                      <RelationshipGraph relationships={relationships} onSelectThread={(thread) => setSelectedThreadGroup({ groupName: getThreadName(thread), threads: [thread] })} emptyMessage={result?.deadlockDetected ? "The backend reports a deadlock, but matching lock-owner edges are not available in the captured stack traces." : "No deadlock relationships to display."} />
                    </article>
                  );
                })}
              </div>
            </section>

            <section className="report-section" id="call-tree">
              <div className="section-heading">
                <h2>Call Tree</h2>
                <p>Stack frames grouped under each thread. This is a stack-derived view, not a runtime call profiler.</p>
              </div>
              {callTreeThreads.length > 0 ? (
                <div className="call-tree-list">
                  {callTreeThreads.map((thread, index) => {
                    const frames = normalizeStackTrace(thread).split("\n").slice(0, 8);
                    return (
                      <details className="call-tree-thread" key={`${thread?.id ?? getThreadName(thread)}-${index}`} open={index === 0}>
                        <summary>{getThreadName(thread)} — {String(thread?.state ?? "UNKNOWN").replaceAll("_", " ")}</summary>
                        <ol>{frames.map((frame, frameIndex) => <li key={`${frame}-${frameIndex}`}><code>{frame}</code></li>)}</ol>
                      </details>
                    );
                  })}
                </div>
              ) : (
                <p className="empty-message">No stack frames are available to build a call tree.</p>
              )}
            </section>

            <section className="report-section" id="identical-stack-traces">
              <div className="section-heading">
                <h2>Identical Stack Trace Threads</h2>
                <p>Threads are grouped when their captured stack traces match exactly after whitespace normalization.</p>
              </div>
              {identicalStackGroups.length > 0 ? (
                <>
                  <div className="identical-stack-chart" role="img" aria-label="Thread counts for identical stack trace groups">
                    {identicalStackGroups.slice(0, 10).map((group, index) => (
                      <div className="identical-stack-bar-item" key={group.trace}>
                        <span className="identical-stack-count">{group.threads.length}</span>
                        <div className="identical-stack-bar-track"><div className="identical-stack-bar" style={{ height: `${Math.max(6, (group.threads.length / identicalStackGroups[0].threads.length) * 100)}%` }} /></div>
                        <span className="identical-stack-label">Stack {index + 1}</span>
                      </div>
                    ))}
                  </div>
                  <div className="identical-stack-groups">
                    {identicalStackGroups.map((group, index) => (
                      <article className="identical-stack-group" key={group.trace}>
                        <h3>{group.threads.length} threads share stack trace {index + 1}</h3>
                        <p>{group.threads.map(getThreadName).join(", ")}</p>
                        <details><summary>View grouped stack trace</summary><pre>{group.trace}</pre></details>
                      </article>
                    ))}
                  </div>
                </>
              ) : (
                <p className="empty-message">No identical stack-trace groups were found in this dump.</p>
              )}
            </section>
          </section>
        )}




        <section className="report-section" id="history">
          <div className="section-heading">
            <h2>Analysis History</h2>
            <p>View previously saved analysis records.</p>
          </div>

          <button
            type="button"
            className="secondary-button"
            onClick={loadHistory}
            disabled={historyLoading}
          >
            {historyLoading ? "Loading History..." : "Load History"}
          </button>

          {historyError && <p className="error">{historyError}</p>}

          {historyRequested && history.length === 0 && !historyLoading && (
            <p className="empty-message">No history records found.</p>
          )}

          {history.map((item, index) => (
            <article
              className="history-item"
              key={item?.id ?? item?.analysisId ?? index}
            >
              <h3>
                Analysis #{item?.id ?? item?.analysisId ?? index + 1}
              </h3>

              <p>
                <strong>Total Threads:</strong>{" "}
                {item?.totalThreads ?? "N/A"}
              </p>

              <p>
                <strong>Daemon Threads:</strong>{" "}
                {item?.daemonThreads ?? "N/A"}
              </p>

              <p>
                <strong>Non-Daemon Threads:</strong>{" "}
                {item?.nonDaemonThreads ?? "N/A"}
              </p>

              <p>
                <strong>Deadlock Detected:</strong>{" "}
                {item?.deadlockDetected == null
                  ? "N/A"
                  : item.deadlockDetected
                    ? "Yes"
                    : "No"}
              </p>

              <p>
                <strong>Created At:</strong>{" "}
                {item?.createdAt ??
                  item?.analysisDate ??
                  item?.timestamp ??
                  "N/A"}
              </p>
            </article>
          ))}
        </section>

        <footer className="page-footer">
          ThreadViewer · Java Thread Dump Analysis Tool
        </footer>
      </main>
    </div>
  );
}

export default App;

