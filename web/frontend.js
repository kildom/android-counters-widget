(() => {
  "use strict";

  const uidPattern = /^[a-zA-Z0-9]+$/;

  function getUid() {
    let uid;
    try {
      uid = decodeURIComponent(window.location.hash.slice(1));
    } catch {
      return null;
    }
    return uidPattern.test(uid) ? uid : null;
  }

  function requestUid() {
    const uid = window.prompt("Enter your counter UID:");
    if (uid === null || uid.trim() === "") {
      return null;
    }
    const cleanedUid = uid.trim();
    if (!uidPattern.test(cleanedUid)) {
      window.alert("UID must contain only letters and numbers.");
      return null;
    }
    window.location.hash = encodeURIComponent(cleanedUid);
    window.location.reload();
    return null;
  }

  async function apiRequest(uid, method = "GET", payload) {
    const options = { method, cache: "no-store" };
    if (payload) {
      options.headers = { "Content-Type": "application/json" };
      options.body = JSON.stringify(payload);
    }

    const response = await fetch(`backend.php?uid=${encodeURIComponent(uid)}`, options);
    let result;
    try {
      result = await response.json();
    } catch {
      throw new Error(`The server returned an invalid response (HTTP ${response.status}).`);
    }
    if (!response.ok || !result || result.status !== "ok") {
      throw new Error(result && result.message ? result.message : `The request failed (HTTP ${response.status}).`);
    }
    return result;
  }

  function initIndex(uid) {
    const message = document.querySelector("#message");
    if (!uid) {
      message.textContent = window.location.hash
        ? "The UID in this link is invalid. Enter a valid UID to continue."
        : "A UID is needed to open your counters.";
      requestUid();
      return;
    }

    document.querySelector("#links").hidden = false;
    document.querySelector("#manage-link").href = `frontend.html#${encodeURIComponent(uid)}`;
    document.querySelector("#backend-url").value =
      new URL(`backend.php?uid=${encodeURIComponent(uid)}`, window.location.href).href;
    message.textContent = "Your counter links are ready.";
    document.querySelector("#copy-url").addEventListener("click", async () => {
      const url = document.querySelector("#backend-url").value;
      try {
        await navigator.clipboard.writeText(url);
        message.textContent = "Backend URL copied.";
      } catch {
        const field = document.querySelector("#backend-url");
        field.focus();
        field.select();
        message.textContent = "Copy was blocked. Select and copy the URL above.";
      }
    });
  }

  function utcDate(dateString) {
    const [year, month, day] = dateString.split("-").map(Number);
    return Date.UTC(year, month - 1, day);
  }

  function daysUntil(dateString) {
    const today = new Date();
    const todayUtc = Date.UTC(today.getFullYear(), today.getMonth(), today.getDate());
    return Math.round((utcDate(dateString) - todayUtc) / 86400000);
  }

  function dateAfterDays(days) {
    const today = new Date();
    const date = new Date(Date.UTC(today.getFullYear(), today.getMonth(), today.getDate() + days));
    return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, "0")}-${String(date.getUTCDate()).padStart(2, "0")}`;
  }

  function setStatus(message, isError = false) {
    const status = document.querySelector("#status");
    status.textContent = message;
    status.classList.toggle("error", isError);
  }

  function makeInput(type, value, label, field) {
    const input = document.createElement("input");
    input.type = type;
    input.value = String(value);
    input.setAttribute("aria-label", label);
    input.dataset.field = field;
    if (type === "number") {
      input.step = "1";
    }
    return input;
  }

  function appendCell(row, label, child) {
    const cell = document.createElement("td");
    cell.dataset.label = label;
    cell.append(child);
    row.append(cell);
    return cell;
  }

  function renderCounters(counters, uid) {
    const body = document.querySelector("#counter-rows");
    body.replaceChildren();

    const ordered = counters.map((counter) => ({
      ...counter,
      daysLeft: daysUntil(counter.targetDate)
    })).sort((a, b) => a.daysLeft - b.daysLeft || a.name.localeCompare(b.name));

    for (const counter of ordered) {
      const row = document.createElement("tr");
      row.dataset.id = counter.id;
      const name = makeInput("text", counter.name, "Name", "name");
      const period = makeInput("number", counter.period, "Period in days", "period");
      period.min = "1";
      const remaining = makeInput("number", counter.daysLeft, "Days left", "daysLeft");
      appendCell(row, "Name", name);
      appendCell(row, "Period (days)", period);
      appendCell(row, "Days left", remaining);

      const actions = document.createElement("div");
      actions.className = "row-actions";
      const save = document.createElement("button");
      save.type = "button";
      save.textContent = "Save";
      save.dataset.action = "save";
      const remove = document.createElement("button");
      remove.type = "button";
      remove.textContent = "Delete";
      remove.className = "delete";
      remove.dataset.action = "delete";
      actions.append(save, remove);
      appendCell(row, "Actions", actions);
      body.append(row);

      row.dataset.originalName = counter.name;
      row.dataset.originalPeriod = String(counter.period);
      row.dataset.originalDaysLeft = String(counter.daysLeft);
    }

    const row = document.createElement("tr");
    row.className = "add-row";
    const name = makeInput("text", "", "New counter name", "name");
    const period = makeInput("number", "", "New counter period in days", "period");
    period.min = "1";
    const daysLeft = makeInput("number", "", "New counter days left", "daysLeft");
    daysLeft.placeholder = "defaults to period";
    appendCell(row, "Name", name);
    appendCell(row, "Period (days)", period);
    appendCell(row, "Days left", daysLeft);
    const add = document.createElement("button");
    add.type = "button";
    add.textContent = "Add";
    add.dataset.action = "add";
    appendCell(row, "Actions", add);
    body.append(row);
    row.dataset.uid = uid;
  }

  function updateDirtyState(row) {
    const originals = {
      name: row.dataset.originalName,
      period: row.dataset.originalPeriod,
      daysLeft: row.dataset.originalDaysLeft
    };
    for (const input of row.querySelectorAll("input[data-field]")) {
      input.classList.toggle("dirty", input.value !== originals[input.dataset.field]);
    }
  }

  function changedFields(row) {
    const originals = {
      name: row.dataset.originalName,
      period: row.dataset.originalPeriod,
      daysLeft: row.dataset.originalDaysLeft
    };
    const values = {};
    for (const input of row.querySelectorAll("input[data-field]")) {
      if (input.value !== originals[input.dataset.field]) {
        values[input.dataset.field] = input.value;
      }
    }
    return values;
  }

  function parsePositiveInteger(value, label) {
    if (!/^\d+$/.test(value) || !Number.isSafeInteger(Number(value)) || Number(value) < 1) {
      throw new Error(`${label} must be a positive whole number.`);
    }
    return Number(value);
  }

  async function initFrontend(uid) {
    const body = document.querySelector("#counter-rows");
    const refresh = async () => {
      setStatus("Loading counters…");
      try {
        const result = await apiRequest(uid);
        renderCounters(result.counters, uid);
        setStatus(`${result.counters.length} counter${result.counters.length === 1 ? "" : "s"}.`);
      } catch (error) {
        setStatus(error.message, true);
      }
    };

    body.addEventListener("input", (event) => {
      const input = event.target.closest("input[data-field]");
      if (input && !input.closest(".add-row")) {
        updateDirtyState(input.closest("tr"));
      }
    });

    body.addEventListener("click", async (event) => {
      const button = event.target.closest("button[data-action]");
      if (!button) {
        return;
      }
      const row = button.closest("tr");
      const action = button.dataset.action;
      const setBusy = (busy) => {
        row.querySelectorAll("input, button").forEach((control) => { control.disabled = busy; });
      };

      if (action === "delete") {
        const name = row.querySelector('[data-field="name"]').value;
        if (!window.confirm(`Delete "${name}"? This cannot be undone.`)) {
          return;
        }
        setBusy(true);
        try {
          await apiRequest(uid, "POST", { command: "delete", id: row.dataset.id });
          await refresh();
        } catch (error) {
          setStatus(error.message, true);
          setBusy(false);
        }
        return;
      }

      if (action === "add") {
        const name = row.querySelector('[data-field="name"]').value.trim();
        const period = row.querySelector('[data-field="period"]').value;
        const daysLeft = row.querySelector('[data-field="daysLeft"]').value;
        let counterWasAdded = false;
        try {
          if (!name) {
            throw new Error("Name cannot be empty.");
          }
          const parsedPeriod = parsePositiveInteger(period, "Period");
          let targetDays = parsedPeriod;
          if (daysLeft !== "") {
            if (!/^-?\d+$/.test(daysLeft) || !Number.isSafeInteger(Number(daysLeft))) {
              throw new Error("Days left must be a whole number.");
            }
            targetDays = Number(daysLeft);
          }
          setBusy(true);
          const added = await apiRequest(uid, "POST", { command: "add", name, period: parsedPeriod });
          counterWasAdded = true;
          if (targetDays !== parsedPeriod) {
            await apiRequest(uid, "POST", {
              command: "edit",
              id: added.id,
              targetDate: dateAfterDays(targetDays)
            });
          }
          await refresh();
          document.querySelector('.add-row [data-field="name"]').focus();
        } catch (error) {
          if (counterWasAdded) {
            await refresh();
          }
          setStatus(error.message, true);
          setBusy(false);
        }
        return;
      }

      if (action === "save") {
        const changed = changedFields(row);
        const payload = { command: "edit", id: row.dataset.id };
        try {
          if (Object.hasOwn(changed, "name")) {
            if (!changed.name.trim()) {
              throw new Error("Name cannot be empty.");
            }
            payload.name = changed.name;
          }
          if (Object.hasOwn(changed, "period")) {
            payload.period = parsePositiveInteger(changed.period, "Period");
          }
          if (Object.hasOwn(changed, "daysLeft")) {
            if (!/^-?\d+$/.test(changed.daysLeft) || !Number.isSafeInteger(Number(changed.daysLeft))) {
              throw new Error("Days left must be a whole number.");
            }
            payload.targetDate = dateAfterDays(Number(changed.daysLeft));
          }
          if (Object.keys(payload).length === 2) {
            setStatus("There are no changes to save.");
            return;
          }
          setBusy(true);
          const result = await apiRequest(uid, "POST", payload);
          renderCounters(result.counters, uid);
          setStatus("Counter saved.");
        } catch (error) {
          setStatus(error.message, true);
          setBusy(false);
        }
      }
    });

    await refresh();
  }

  const uid = getUid();
  if (document.querySelector("#links")) {
    initIndex(uid);
  } else if (document.querySelector("#counter-rows")) {
    if (uid) {
      initFrontend(uid);
    } else {
      setStatus("A valid UID is required.");
      requestUid();
    }
  }
})();