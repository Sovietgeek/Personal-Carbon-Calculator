const API = "http://localhost:8080/auth";

let isLoginMode = true;

// ================= TAB SWITCH =================
document.addEventListener("DOMContentLoaded", () => {
  const tabLogin = document.getElementById("tabLogin");
  const tabRegister = document.getElementById("tabRegister");
  const btn = document.getElementById("mainBtn"); // ✅ FIXED ID

  tabLogin.onclick = () => {
    isLoginMode = true;

    tabLogin.classList.add("active");
    tabRegister.classList.remove("active");

    btn.innerText = "🌱 Sign In to EcoTrack";
    show("", "white");
  };

  tabRegister.onclick = () => {
    isLoginMode = false;

    tabRegister.classList.add("active");
    tabLogin.classList.remove("active");

    btn.innerText = "🚀 Create Account";
    show("", "white");
  };
});

// ================= BUTTON =================
function handleAuth() {
  if (isLoginMode) login();
  else register();
}

// ================= MESSAGE =================
function show(msg, color) {
  const m = document.getElementById("message");
  m.innerText = msg;
  m.style.color = color;
}

// ================= LOGIN =================
function login() {
  const username = document.getElementById("username").value.trim();
  const password = document.getElementById("password").value.trim();

  if (!username || !password) {
    show("Fill all fields ❌", "red");
    return;
  }

  fetch(`${API}/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password })
  })
  .then(res => res.text())
  .then(token => {

    if (token === "FAIL") {
      show("Invalid login ❌", "red");
      return;
    }

    localStorage.setItem("token", token);
    localStorage.setItem("user", username);

    show("Login success ✅", "#00ffaa");

    setTimeout(() => {
      window.location.href = "indexx.html";
    }, 800);
  })
  .catch(() => {
    show("Server unreachable ❌", "red");
  });
}

// ================= REGISTER =================
function register() {
  const username = document.getElementById("username").value.trim();
  const password = document.getElementById("password").value.trim();

  if (!username || !password) {
    show("Fill all fields ❌", "red");
    return;
  }

  fetch(`${API}/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password })
  })
  .then(res => res.text())
  .then(data => {

    if (data === "REGISTERED") {

      // ✅ SUCCESS MESSAGE
      show("Account created 🎉 Now Sign In!", "#00ffaa");

      // 🔥 AUTO SWITCH TO LOGIN TAB
      isLoginMode = true;

      document.getElementById("tabLogin").classList.add("active");
      document.getElementById("tabRegister").classList.remove("active");

      document.getElementById("mainBtn").innerText = "🌱 Sign In to EcoTrack";

    } else {
      show("User already exists ❌", "red");
    }

  })
  .catch(() => {
    show("Server unreachable ❌", "red");
  });
}