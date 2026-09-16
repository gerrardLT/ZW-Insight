#!/bin/bash
# ============================================
# ZW-Insight Load Test Runner Script
# Usage: ./run-load-tests.sh [env] [base_url]
# Example: ./run-load-tests.sh dev http://localhost:8080
# ============================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Default configuration
ENV="${1:-dev}"
BASE_URL="${2:-http://localhost:8080}"
TEST_DURATION="${3:-30m}"
REPORT_DIR="reports/k6"

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}ZW-Insight Performance Load Test Runner${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo "Configuration:"
echo "  - Environment: ${ENV}"
echo "  - Base URL: ${BASE_URL}"
echo "  - Report Dir: ${REPORT_DIR}"
echo ""

# Create report directory
mkdir -p ${REPORT_DIR}

# Function to check prerequisites
check_prerequisites() {
    echo -e "${YELLOW}Checking prerequisites...${NC}"
    
    if ! command -v k6 &> /dev/null; then
        echo -e "${RED}ERROR: k6 is not installed. Please install from https://k6.io/docs/getting-started/installation/${NC}"
        exit 1
    fi
    
    if ! command -v jq &> /dev/null; then
        echo -e "${YELLOW}WARNING: jq not found. JSON parsing in scripts may be limited.${NC}"
    fi
    
    echo -e "${GREEN}Prerequisites OK${NC}"
    echo ""
}

# Function to run specific test scenarios
run_budget_change_test() {
    echo -e "${YELLOW}Running Budget Change Load Test...${NC}"
    echo ""
    
    export K6_BASE_URL=${BASE_URL}
    
    k6 run --summary-export=${REPORT_DIR}/budget-change-summary.json \
           tests/k6/budget-change-load.js
    
    echo -e "${GREEN}Budget Change test completed!${NC}"
    echo ""
    
    # Extract summary
    if command -v jq &> /dev/null; then
        echo "=== Summary Statistics ==="
        cat ${REPORT_DIR}/budget-change-summary.json | jq '.metrics.http_req_duration.values'
        echo ""
    fi
}

run_dashboard_test() {
    echo -e "${YELLOW}Running Dashboard Load Test...${NC}"
    echo ""
    
    # TODO: Implement dashboard-stress.js when needed
    echo "Dashboard stress test not yet implemented"
    echo ""
}

# Function to generate HTML report
generate_html_report() {
    local timestamp=$(date +%Y%m%d_%H%M%S)
    local html_report="${REPORT_DIR}/report_${timestamp}.html"
    
    echo -e "${YELLOW}Generating HTML report...${NC}"
    
    cat > ${html_report} << 'EOF'
<!DOCTYPE html>
<html>
<head>
    <title>ZW-Insight Performance Test Report</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .metric { background: #f5f5f5; padding: 10px; margin: 10px 0; border-radius: 5px; }
        .pass { color: green; }
        .fail { color: red; }
        h1 { color: #333; }
    </style>
</head>
<body>
    <h1>Performance Test Results</h1>
    <div id="results"></div>
    <script>
        // Parse JSON results here (to be populated)
    </script>
</body>
</html>
EOF
    
    echo -e "${GREEN}HTML report saved to: ${html_report}${NC}"
}

# Main execution
echo "Starting load tests..."
echo ""

check_prerequisites

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}EXECUTING TESTS${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# Run all available tests
run_budget_change_test

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}ALL TESTS COMPLETED${NC}"
echo -e "${GREEN}============================================${NC}"

if command -v jq &> /dev/null; then
    echo ""
    echo "View detailed metrics with:"
    echo "  cat ${REPORT_DIR}/budget-change-summary.json | jq '.metrics'"
fi

echo ""
echo "Report files available in: ${REPORT_DIR}/"
