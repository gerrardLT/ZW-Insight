"use strict";

// 共享数据：虚构数据（所有数字、名称均为演示用途）
const statsData = {
  projectTotal: 128,
  totalContractAmount: 3456,
  totalIncome: 2890,
  advanceFund: 566
};

const overdueStats = { count: 3, amount: 12.8, maxDays: 47 };

const projectsByStatus = {
  DRAFT: 12,
  FILED: 35,
  TENDERING: 28,
  WON: 18,
  CONSTRUCTION: 25,
  COMPLETED: 8,
  CLOSING: 2,
  CLOSED: 0
};

const revenueExpense = {
  income: 2890,
  expense: 2345
};

const pendingTasks = [
  {
    id: "t1",
    title: "施工合同登记审批",
    meta: "项目：滨海路二期工程 · 发起人：张工",
    status: "reviewing",
    label: "审核中",
    priority: "high"
  },
  {
    id: "t2",
    title: "材料采购合同结算",
    meta: "合同编号：CT-2026-0142 · 金额：45.8 万",
    status: "approval",
    label: "待批准",
    priority: "normal"
  },
  {
    id: "t3",
    title: "三方比价中标公示",
    meta: "询价公告：QJ-2026-0089 · 供应商：3 家报价",
    status: "reviewing",
    label: "审核中",
    priority: "normal"
  }
];

// DOM 元素缓存（PC 端）
let greetingEl = null;
let todayTextEl = null;
if (document.querySelector('[data-version="pc-homepage"]')) {
  greetingEl = document.getElementById("greeting");
  todayTextEl = document.getElementById("today-text");
}

// 初始化欢迎语与日期（PC 端）
function initWelcome() {
  if (!greetingEl || !todayTextEl) return;
  
  const hour = new Date().getHours();
  const greetings = ["凌晨好", "早上好", "上午好", "中午好", "下午好", "晚上好"];
  const index = hour < 6 ? 0 : hour < 9 ? 1 : hour < 12 ? 2 : hour < 14 ? 3 : hour < 18 ? 4 : 5;
  greetingEl.textContent = `${greetings[index]}，管理员`;
  
  const date = new Date();
  const week = ["星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"];
  todayTextEl.textContent = `${date.getFullYear()}年${date.getMonth()+1}月${date.getDate()}日 ${week[date.getDay()]}`;
}

// 格式化金额（万元）
function formatWan(value) {
  return (value / 10000).toFixed(1);
}

// 构建饼图配置
function buildPieOption(theme = "light") {
  const colors = theme === "dark" 
    ? ["#3b82f6", "#10b981", "#f59e0b", "#ef4444", "#8b5cf6", "#ec4899", "#6366f1"]
    : ["#3b82f6", "#10b981", "#f59e0b", "#ef4444", "#8b5cf6", "#ec4899", "#6366f1"];
  
  const labels = ["草稿", "已报备", "招标中", "已中标", "施工中", "已竣工", "结项审批中"];
  const data = Object.entries(projectsByStatus).map(([name, value], idx) => ({
    name: labels[idx] || name,
    value: Number(value) || 0
  }));
  
  return {
    color: colors,
    tooltip: { trigger: "item", formatter: "{b}: {c} ({d}%)" },
    legend: { bottom: 0 },
    series: [{
      type: "pie",
      radius: ["45%", "70%"],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 4, borderColor: "#fff", borderWidth: 2 },
      label: { show: true, formatter: "{b}: {c}" },
      data: data.filter(d => d.value > 0)
    }]
  };
}

// 构建柱状图配置
function buildBarOption(data, theme = "light") {
  const colors = theme === "dark" 
    ? ["#3b82f6", "#ef4444"]
    : ["#3b82f6", "#ef4444"];
  
  return {
    tooltip: { trigger: "axis", axisPointer: { type: "shadow" }, formatter: "{a}<br/>{b} : {c} 万" },
    grid: { left: "3%", right: "4%", bottom: "3%", containLabel: true },
    xAxis: { type: "category", data: ["收入", "支出"], axisLine: { lineStyle: { color: "#999" } }, splitLine: { show: false } },
    yAxis: { type: "value", name: "万元", nameTextStyle: { fontSize: 12 }, axisLabel: { formatter: "{value}" }, splitLine: { lineStyle: { color: "#eee" } } },
    series: [{
      name: "金额",
      type: "bar",
      barWidth: "40%",
      data: [Number(data.income), Number(data.expense)],
      itemStyle: { color: colors }
    }]
  };
}

// 渲染图表（PC 端）
function renderCharts() {
  if (!document.querySelector('[data-version="pc-homepage"]')) return;
  
  const pieContainer = document.getElementById("pieChart");
  const barContainer = document.getElementById("barChart");
  
  if (pieContainer && echarts) {
    const pieChart = echarts.init(pieContainer);
    pieChart.setOption(buildPieOption());
    window.addEventListener("resize", () => pieChart.resize());
  }
  
  if (barContainer && echarts) {
    const barChart = echarts.init(barContainer);
    barChart.setOption(buildBarOption(revenueExpense));
    window.addEventListener("resize", () => barChart.resize());
  }
}

// 渲染任务列表（Mobile 端）
function renderPendingTasks() {
  const container = document.getElementById("taskList");
  if (!container) return;
  
  const emptyState = document.getElementById("empty-state");
  container.innerHTML = "";
  
  if (pendingTasks.length === 0) {
    emptyState.classList.add("visible");
    return;
  }
  
  emptyState.classList.remove("visible");
  
  pendingTasks.forEach(task => {
    const card = document.createElement("div");
    card.className = `task-card ${task.priority === "high" ? "priority-high" : ""} ${task.status === "reviewing" ? "status-reviewing" : ""}`;
    
    const header = document.createElement("div");
    header.className = "task-header";
    
    const title = document.createElement("div");
    title.className = "task-title";
    title.textContent = task.title;
    
    const meta = document.createElement("div");
    meta.className = "task-meta";
    meta.textContent = task.meta;
    
    const badge = document.createElement("span");
    badge.className = `status-badge ${task.status}`;
    badge.textContent = task.label;
    
    header.append(title, meta, badge);
    
    const footer = document.createElement("div");
    footer.className = "task-footer";
    
    const actionsBtn = document.createElement("button");
    actionsBtn.textContent = "办理";
    actionsBtn.onclick = () => alert(`办理任务：${task.title}`);
    
    footer.append(actionsBtn);
    
    card.append(header, footer);
    container.appendChild(card);
  });
}

// 页面加载完成后的行为
document.addEventListener("DOMContentLoaded", () => {
  initWelcome();
  renderCharts();
  renderPendingTasks();
});
