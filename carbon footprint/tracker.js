let activities = [];

function addActivity(){

  const name = document.getElementById("activity").value;
  const type = document.getElementById("type").value;
  const value = parseFloat(document.getElementById("value").value);

  if(!name || !value){
    alert("Fill all fields");
    return;
  }

  activities.push({name, type, value});

  renderList();
  loadChart();
}

// LIST
function renderList(){
  const list = document.getElementById("activityList");
  list.innerHTML = "";

  activities.forEach(a=>{
    list.innerHTML += `
      <p>${a.name} (${a.type}) → ${a.value} kg</p>
    `;
  });
}

// CHART
let pie;

function loadChart(){

  const ctx = document.getElementById("pieChart");

  const types = ["travel","food","energy"];

  const data = types.map(t =>
    activities.filter(a=>a.type===t)
              .reduce((s,a)=>s+a.value,0)
  );

  if(pie) pie.destroy();

  pie = new Chart(ctx,{
    type:"pie",
    data:{
      labels:types,
      datasets:[{
        data:data
      }]
    }
  });
}