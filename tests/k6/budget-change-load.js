// ============================================
// ZW-Insight Performance Load Test
// Module: Budget Change (P0 Critical Path)
// Purpose: Validate API p95 latency < 500ms after optimizations
// ============================================

import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  // VU distribution over time
  stages: [
    { duration: '2m', target: 25 },   // Ramp-up to 25 users
    { duration: '5m', target: 50 },   // Ramp-up to 50 users
    { duration: '10m', target: 100 }, // Sustain 100 users (simulates peak load)
    { duration: '5m', target: 50 },   // Scale down
    { duration: '3m', target: 0 },    // Ramp-down to 0
  ],
  
  // Performance thresholds (must pass)
  thresholds: {
    // p95 latency < 500ms (critical for user experience)
    http_req_duration: ['p(95)<500'],
    
    // p99 latency < 1000ms (acceptable under load)
    http_req_duration: ['p(99)<1000'],
    
    // Error rate < 1%
    http_req_failed: ['rate<0.01'],
    
    // Slow queries (>1s) should be <5% of requests
    'slow_queries': ['rate<0.05'],
  },
  
  // Custom metrics
  summaries: true,
  metrics: {
    drops: { false },
  },
};

const BASE_URL = __ENV.K6_BASE_URL || 'http://localhost:8080';
const TOKEN_HEADER = 'Authorization';
const BEARER_PREFIX = 'Bearer ';

// Base64 encode credentials for login (will be overwritten by setup function)
let authToken = '';

// ============================================
// HELPER FUNCTIONS
// ============================================

function b64Encode(str) {
  return btoa(unescape(encodeURIComponent(str)));
}

function logMetric(name, value, unit = 'ms') {
  console.log(`[${name}] ${value.toFixed(2)}${unit}`);
}

// ============================================
// SETUP & TEARDOWN
// ============================================

export function setup() {
  console.log('[SETUP] Logging in and obtaining auth token...');
  
  const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`, {
    username: 't9999admin',
    password: '123456',
  }, {
    headers: {
      'Content-Type': 'application/json',
    },
  });
  
  if (loginRes.status !== 200) {
    throw new Error(`Login failed with status ${loginRes.status}: ${loginRes.body}`);
  }
  
  const responseData = JSON.parse(loginRes.body);
  if (!responseData.data?.access_token) {
    throw new Error('Token not found in login response');
  }
  
  authToken = responseData.data.access_token;
  
  console.log(`[SETUP] Auth token obtained, starting load test...`);
  
  return { authToken };
}

export function handleSummary(data) {
  // Generate detailed report
  return {
    'summary.json': JSON.stringify(data),
    'summary.txt': generateTextReport(data),
  };
}

function generateTextReport(data) {
  let output = 'ZW-Insight Performance Test Results\n';
  output += '='.repeat(50) + '\n\n';
  
  // Latency stats
  const latencies = data.metrics.http_req_duration.values;
  output += 'Latency Summary:\n';
  output += `  - min: ${latencies.p(0).toFixed(2)}ms\n`;
  output += `  - max: ${latencies.p(100).toFixed(2)}ms\n`;
  output += `  - median (p50): ${latencies.p(50).toFixed(2)}ms\n`;
  output += `  - p95: ${latencies.p(95).toFixed(2)}ms\n`;
  output += `  - p99: ${latencies.p(99).toFixed(2)}ms\n\n`;
  
  // Request stats
  const reqs = data.metrics.http_reqs.values;
  output += `Total Requests: ${Math.floor(reqs.total)}\n`;
  output += `Requests/sec: ${(reqs.total / data.testDuration).toFixed(2)}\n\n`;
  
  // Error stats
  const errors = data.metrics.http_req_failed.values;
  output += `Error Rate: ${(errors.rate * 100).toFixed(2)}%\n`;
  
  // Threshold status
  output += '\nThreshold Status:\n';
  Object.keys(data.metrics).forEach(metricName => {
    if (metricName.startsWith('http_req_')) {
      const metric = data.metrics[metricName];
      if (metric.thresholds) {
        Object.entries(metric.thresholds).forEach(([thresholdName, thresholdData]) => {
          const passed = thresholdData.above === false;
          output += `  ${passed ? '✅' : '❌'} ${thresholdName}: ${thresholdData.percentile} = ${thresholdData.value}%\n`;
        });
      }
    }
  });
  
  return output;
}

// ============================================
// TEST SCENARIOS
// ============================================

export default function testData(userContext) {
  authToken = userContext.authToken;
  
  const headers = {
    [TOKEN_HEADER]: `${BEARER_PREFIX}${authToken}`,
    'Content-Type': 'application/json',
  };
  
  // Scenario 1: Paginated list query (primary critical path)
  // Simulates budget change management page loading
  const listRes = http.get(
    `${BASE_URL}/api/v1/budget/change/page?page=1&size=20`,
    { headers }
  );
  
  const listCheck = check(listRes, {
    'budget-change page: status is 200': (r) => r.status === 200,
    'budget-change page: response has records array': (r) => {
      const body = JSON.parse(r.body);
      return body.code === 200 && Array.isArray(body.data?.records);
    },
    'budget-change page: p95 latency < 500ms': (r) => r.timings.duration < 500,
    'budget-change page: contains total count': (r) => {
      const body = JSON.parse(r.body);
      return typeof body.data?.total === 'number';
    },
  });
  
  if (!listCheck) {
    console.error('[ERROR] List query failed:', listRes.body);
  }
  
  // Record slow query metric
  if (listRes.timings.duration > 1000) {
    data.slow_queries = (data.slow_queries || 0) + 1;
  }
  
  sleep(2); // Think time between requests
  
  // Scenario 2: Get detail by ID (read-through cache test)
  const detailRes = http.get(
    `${BASE_URL}/api/v1/budget/change/90001`,
    { headers }
  );
  
  const detailCheck = check(detailRes, {
    'budget-change detail: status is 200': (r) => r.status === 200,
    'budget-change detail: has change number': (r) => {
      const body = JSON.parse(r.body);
      return body.data?.changeNumber != null;
    },
    'budget-change detail: p95 latency < 200ms': (r) => r.timings.duration < 200,
  });
  
  if (!detailCheck) {
    console.error('[ERROR] Detail query failed:', detailRes.body);
  }
  
  sleep(1);
  
  // Scenario 3: Query change trace by project (aggregation query stress)
  const traceRes = http.get(
    `${BASE_URL}/api/v1/budget/change/trace?projectId=1`,
    { headers }
  );
  
  const traceCheck = check(traceRes, {
    'budget-change trace: status is 200': (r) => r.status === 200,
    'budget-change trace: returns array': (r) => {
      const body = JSON.parse(r.body);
      return Array.isArray(body.data);
    },
    'budget-change trace: p95 latency < 800ms': (r) => r.timings.duration < 800,
  });
  
  if (!traceCheck) {
    console.error('[ERROR] Trace query failed:', traceRes.body);
  }
  
  sleep(3); // Longer think time for aggregation queries
  
  // Scenario 4: Cache warmup - repeat detail query (simulate multiple users viewing same record)
  // This validates Redis caching effectiveness (second request should be faster)
  const cachedDetailRes = http.get(
    `${BASE_URL}/api/v1/budget/change/90001`,
    { headers }
  );
  
  check(cachedDetailRes, {
    'budget-change cached detail: fast response (< 50ms)': (r) => r.timings.duration < 50,
  });
  
  sleep(5); // Simulate user browsing behavior
}

// ============================================
// METRIC COLLECTION
// ============================================

export function onSummary(data) {
  console.log('\n====================================');
  console.log('LOAD TEST COMPLETED');
  console.log('====================================');
  console.log(`Test Duration: ${data.testDuration ? (data.testDuration / 1000).toFixed(1) + 's' : 'N/A'}`);
  console.log(`Total VUs: ${data.metrics.vus.values.max}`);
  console.log(`Max RPS: ${data.metrics.requests_per_second ? data.metrics.requests_per_second.values.max : 'N/A'}`);
  console.log('See summary.txt for detailed breakdown');
}
