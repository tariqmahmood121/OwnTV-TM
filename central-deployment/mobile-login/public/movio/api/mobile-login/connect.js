"use strict";

const phoneSecret = location.hash.slice(1);
history.replaceState(null, "", location.pathname);

const statusLine = document.querySelector("#status");
const form = document.querySelector("#login");
const username = document.querySelector("#username");
const password = document.querySelector("#password");
const visibility = document.querySelector("#visibility");
const submit = form.querySelector(".submit");
const unavailable = "Central is unavailable. Try again or sign in directly on your TV.";
const expired = "This pairing has ended. Open mobile sign-in again on your TV to get a new QR code.";

function show(message, isError = false) {
    statusLine.textContent = message;
    statusLine.dataset.error = String(isError);
}

async function request(action, fields = {}) {
    const response = await fetch("pair.php", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ action, phone_secret: phoneSecret, ...fields }),
        cache: "no-store",
        credentials: "omit",
        redirect: "error",
    });
    return (await response.json()).state;
}

async function check() {
    try {
        const state = await request("status");
        if (state === "waiting") {
            show("Enter your Central account details.");
            form.hidden = false;
        } else if (state === "claimed") {
            form.hidden = true;
            show("Connected. Continue on your TV.");
        } else if (state === "authenticating" || state === "ready") {
            form.hidden = true;
            show("Waiting for your TV to connect…");
            setTimeout(check, 2500);
        } else {
            form.hidden = true;
            show(state === "cancelled" || state === "expired" ? expired : unavailable, true);
        }
    } catch {
        show(unavailable, true);
    }
}

function showPassword(visible) {
    password.type = visible ? "text" : "password";
    visibility.textContent = visible ? "Hide" : "Show";
    visibility.setAttribute("aria-pressed", String(visible));
}

visibility.addEventListener("click", () => showPassword(password.type === "password"));

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (submit.disabled) return;
    submit.disabled = true;
    show("Signing in to Central…");
    let result;
    try {
        result = await request("connect", { username: username.value.trim(), password: password.value });
    } catch {
        result = "unavailable";
    } finally {
        password.value = "";
        showPassword(false);
        submit.disabled = false;
    }

    if (result === "ready") {
        form.hidden = true;
        show("Waiting for your TV to connect…");
        setTimeout(check, 2500);
    } else if (result === "invalid_credentials") {
        show("Central rejected those account details. Check your username and password.", true);
        password.focus();
    } else if (result === "limited") {
        show("Too many attempts. Wait a moment and try again.", true);
    } else if (result === "closed" || result === "expired") {
        form.hidden = true;
        show(expired, true);
    } else {
        show(unavailable, true);
    }
});

if (/^[0-9a-f]{64}$/.test(phoneSecret)) {
    check();
} else {
    show("Scan the QR code shown on your TV to start a secure connection.", true);
}
