function go(page){
  // simple safe navigation
  window.location.href = page;
}

// SIDEBAR TOGGLE (3 dots)
function toggleSidebar(){
  const sb = document.getElementById("sidebar");
  sb.classList.toggle("collapsed");
}