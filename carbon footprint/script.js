const API = "http://localhost:8080/goals";
const token = localStorage.getItem("token");

if (!token) window.location.href = "login.html";

// ================= INIT =================
window.onload = () => {
    document.getElementById("user-greeting").innerText =
        `Welcome, ${localStorage.getItem("user") || 'Eco Hero'}!`;

    fetchGoals();
    updateAITip();
    loadLeaderboard();
};

// ================= FETCH =================
function fetchGoals() {
    fetch(API, {
        headers: { "Authorization": "Bearer " + token }
    })
    .then(res => res.json())
    .then(data => {
        updateStats(data);
        displayGoalCards(data);
        loadMonthlyChart(data);
        loadHistory(data);
    });
}

// ================= DISPLAY =================
function displayGoalCards(goals) {
    const list = document.getElementById("goalList");
    list.innerHTML = "";

    goals.forEach(goal => {
        const percent = Math.min((goal.progress / goal.target) * 100, 100);

        list.innerHTML += `
            <div class="goal-card">
                <h3>${goal.title}</h3>

                <div class="progress-bar">
                    <div class="progress-fill" style="width:${percent}%"></div>
                </div>

                <div class="goal-actions">
                    <input type="number" id="p${goal.id}" placeholder="kg">
                    <button onclick="updateProgress(${goal.id})">Update</button>
                    <button onclick="deleteGoal(${goal.id})" style="background:red;color:white;">Delete</button>
                </div>

                <small>${goal.progress}/${goal.target} kg</small>
            </div>
        `;
    });
}

// ================= ADD =================
function addGoal() {
    console.log("Add Goal clicked");

    const title = document.getElementById("title").value;
    const target = document.getElementById("target").value;
    const category = document.getElementById("category").value;

    if (!title || !target) {
        alert("Enter all fields ❌");
        return;
    }

    const token = localStorage.getItem("token");

    fetch("http://localhost:8080/goals", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            "Authorization": "Bearer " + token
        },
        body: JSON.stringify({
            title: category + " - " + title,
            target: Number(target),
            progress: 0
        })
    })
    .then(res => {
        console.log("Response status:", res.status);
        if (!res.ok) throw new Error("Failed request");
        return res.json();
    })
    .then(data => {
        console.log("Added:", data);
        alert("Goal Added ✅");   // 🔥 feedback

        document.getElementById("title").value = "";
        document.getElementById("target").value = "";

        fetchGoals(); // refresh UI
    })
    .catch(err => {
        console.error(err);
        alert("Error adding goal ❌");
    });
}

// ================= DELETE =================
function deleteGoal(id) {
    fetch(`${API}/${id}`, {
        method: "DELETE",
        headers: { "Authorization": "Bearer " + token }
    }).then(fetchGoals);
}

// ================= UPDATE =================
function updateProgress(id) {
    const val = document.getElementById("p" + id).value;

    fetch(`${API}/${id}/progress/${val}`, {
        method: "PUT",
        headers: { "Authorization": "Bearer " + token }
    }).then(fetchGoals);
}

// ================= STATS =================
function updateStats(goals) {
    document.getElementById("total").innerText = goals.length;

    document.getElementById("completed").innerText =
        goals.filter(g => g.progress >= g.target).length;

    const saved = goals.reduce((sum, g) => sum + g.progress, 0);

    document.getElementById("daily").innerText = `${saved} kg`;
}

// ================= HISTORY =================
function loadHistory(goals) {
    const list = document.getElementById("historyList");
    list.innerHTML = "";

    goals.slice(-5).reverse().forEach(g => {
        list.innerHTML += `<li>🌿 ${g.title} → ${g.progress} kg</li>`;
    });
}

// ================= 📊 MONTHLY CHART =================
let chart;

function loadMonthlyChart(goals) {
    const ctx = document.getElementById("chart").getContext("2d");

    if (chart) chart.destroy();

    chart = new Chart(ctx, {
        type: "line",
        data: {
            labels: goals.map((_, i) => "Day " + (i + 1)),
            datasets: [{
                label: "Carbon Saved",
                data: goals.map(g => g.progress),
                borderColor: "#00c896",
                fill: false
            }]
        }
    });
}

// ================= 🏆 LEADERBOARD =================
function loadLeaderboard() {
    const board = document.getElementById("leaderboard");

    const users = [
        { name: "You", score: 120 },
        { name: "User2", score: 90 },
        { name: "User3", score: 60 }
    ];

    board.innerHTML = "";

    users.forEach(u => {
        board.innerHTML += `<p>${u.name} - ${u.score} pts</p>`;
    });
}

// ================= 🌍 CARBON CALCULATOR =================
function calculateCarbon() {
    const km = document.getElementById("km").value;

    const result = km * 0.21; // avg car emission

    document.getElementById("carbonResult").innerText =
        `${result.toFixed(2)} kg CO2`;
}

// ================= AI =================
function updateAITip() {
    const tips = [
        "Use cycle 🚴",
        "Avoid plastic ♻️",
        "Save electricity ⚡",
        "Plant trees 🌳"
    ];

    document.getElementById("ai-tip").innerText =
        tips[Math.floor(Math.random() * tips.length)];
}

// ================= LOGOUT =================
function logout() {
    localStorage.clear();
    window.location.href = "login.html";
}