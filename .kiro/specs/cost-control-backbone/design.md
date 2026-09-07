# Cost Control Backbone - Design

## Overview

This spec implements the "Cost Control Backbone" for a Construction Operating System:
1. **WBS (Work Breakdown Structure)**: Project → Phase → Work Package hierarchy
2. **CBS (Cost Breakdown Structure)**: Cost accounts mapped to WBS nodes, linking budget baseline/current/commitment/actual
3. **Change Event**: Field-originated change with impact assessment driving cost/schedule impacts
4. **Transactional Outbox**: Domain event infrastructure for eventual consistency

## Module Boundaries

| Domain | Module | Responsibility |
|--------|--------|----------------|
| Project Structure | `zw-project` (existing) | WBS nodes, project structure management |
| Cost Management | `zw-budget` (existing, renamed conceptually) | CBS, Budget Baseline/Current, Commitments, Forecast/EAC |
| Change Management | `zw-contract` (existing, extended) | ChangeEvent domain + ChangeVisa integration |
| Event Infrastructure | `zw-common` (new components) | TransactionalOutbox, DomainEventPublisher |

## Domain Models

### 1. WBS Node (`zw-project`)

```
biz_project_wbs_node
├── id: BIGINT PK
├── tenant_id: BIGINT
├── project_id: BIGINT FK(project)
├── parent_id: BIGINT NULL (root if NULL)
├── level: INT (1=root/phase, 2=work package)
├── code: VARCHAR(50) ("WP-001", "PH-001")
├── name: VARCHAR(200)
├── description: TEXT
├── start_date: DATE
├── end_date: DATE
├── status: VARCHAR(20) (ACTIVE/INACTIVE/CLOSED)
└── sort_order: INT
```

**Aggregate Root**: `ProjectWbsNode`  
**Invariant**: Parent must exist; code unique per project per level

### 2. CBS Cost Account (`zw-budget`)

```
biz_cost_account
├── id: BIGINT PK
├── tenant_id: BIGINT
├── project_id: BIGINT FK(project)
├── parent_id: BIGINT NULL (root-level account)
├── wbs_node_id: BIGINT NULL (optional linkage to work package)
├── code: VARCHAR(50) ("CA-01", "01.02.03")
├── name: VARCHAR(200)
├── cost_category: VARCHAR(30) FK(biz_cost_subcategory.category) [MATERIAL/LABOR/MACHINE/SUBCONTRACT/INDIRECT/OTHER]
├── cost_subcategory: VARCHAR(100) FK(biz_cost_subcategory.subcategory_name)
├── baseline_amount: DECIMAL(18,2) (original budget)
├── current_amount: DECIMAL(18,2) (after changes)
├── commitment_amount: DECIMAL(18,2) (committed via contract/PO/settlement)
├── actual_amount: DECIMAL(18,2) (actual cost from invoices/payments/settlements)
├── forecast_amount: DECIMAL(18,2) (ETC projection)
├── status: VARCHAR(20) (ACTIVE/LOCKED/CLOSED)
└── version: INT (optimistic lock)
```

**Aggregate Root**: `CostAccount`  
**Invariant**: current >= baseline after changes; commitment <= current before payment; version for concurrency control

**Read Model**: `vw_cost_ledger` — denormalized view joining all sources into a unified ledger

### 3. Change Event (`zw-contract`)

```
biz_change_event
├── id: BIGINT PK
├── tenant_id: BIGINT
├── project_id: BIGINT FK(project)
├── event_number: VARCHAR(50) UNIQUE ("CHG-2026-001")
├── source_type: VARCHAR(30) (FIELD_EVENT/DESIGN_CHANGE/OWNER_REQUEST/VARIATION_ORDER/etc.)
├── source_ref: VARCHAR(100) (link to site inspection/other system ID if applicable)
├── title: VARCHAR(300)
├── description: TEXT
├── category: VARCHAR(30) (COST_IMPACT/SCOPE_CHANGE/SCHEDULE_DELAY/QUALITY_ISSUE/etc.)
├── affected_wbs_ids: JSON ["id1","id2"]
├── affected_accounts: JSON [{"accountId":1,"deltaType":"INCREASE","deltaAmount":5000}]
├── supporting_docs: JSON [{url,path,type,uploadedBy,uploadedAt}]
├── impact_assessment: JSON {costDelta:amount, scheduleDelayDays:int, rationale:text}
├── workflow_instance_id: VARCHAR(64) (Flowable instance)
├── status: VARCHAR(20) (DRAFT/PENDING_ASSESSMENT/APPROVAL_PENDING/APPROVED/REJECTED/CANCELLED)
├── created_by: BIGINT
├── assessed_by: BIGINT
├── approved_by: BIGINT
├── approved_at: DATETIME
├── version: INT
└── deleted: INT
```

**State Machine**:
```
DRAFT --create--> PENDING_ASSESSMENT
PENDING_ASSESSMENT --completeAssessment--> APPROVAL_PENDING
APPROVAL_PENDING --approve--> APPROVED
APPROVAL_PENDING --reject--> REJECTED
APPROVAL_PENDING --reassess--> PENDING_ASSESSMENT
any --cancel--> CANCELLED
```

**Domain Events**:
- `ChangeEventCreatedEvent`: triggered on create
- `ChangeEventAssessedEvent`: triggered when impact assessment complete
- `ChangeEventApprovedEvent`: triggers downstream updates (budget change, contract change)
- `ChangeEventRejectedEvent`: clears temporary commitments if any

### 4. Transactional Outbox (`zw-common`)

```
outbox_event
├── id: BIGINT PK
├── tenant_id: BIGINT
├── event_type: VARCHAR(100) (e.g., "CHANGE_EVENT_APPROVED")
├── aggregate_type: VARCHAR(100) ("ChangeEvent")
├── aggregate_id: BIGINT
├── payload: JSONB (full event payload)
├── delivered: BOOLEAN FALSE initially
├── attempts: INT 0 initial
├── next_retry: DATETIME NULL when ready
├── error_message: TEXT NULL
├── created_at: DATETIME
└── updated_at: DATETIME
```

**Producer Pattern**: `@TransactionalEventListener(phase=AFTER_COMMIT)` writes to outbox, separate task consumes and publishes to message broker / external webhooks.

**Consumer**: RabbitMQ dead-letter queue or direct delivery to subscribers with idempotency key = aggregate_type+aggregate_id+eventType.

## Database Schema Changes

See file: `deploy/db-init/XX_V2026_XX__cost_control_backbone.sql`

Migration strategy:
1. Create tables in isolation (no FK yet where possible)
2. Backfill existing data: map current budget detail's `cost_category` to new CBS accounts
3. Add constraints gradually

## API Contracts

### WBS Nodes (`zw-project`)

```
GET /api/v1/project/{projectId}/wbs/nodes              # List tree, ?level=
POST /api/v1/project/{projectId}/wbs/nodes             # Create node
PUT /api/v1/project/{projectId}/wbs/nodes/{id}         # Update
DELETE /api/v1/project/{projectId}/wbs/nodes/{id}      # Delete (soft)
GET /api/v1/project/{projectId}/wbs/nodes/{id}         # Detail with children
```

### Cost Accounts & Ledger (`zw-budget`)

```
GET /api/v1/budget/cost-account                       # Query list (filters: projectId, wbsNodeId, status)
POST /api/v1/budget/cost-account                      # Create (with optional wbs linkage)
PUT /api/v1/budget/cost-account/{id}                  # Update amounts (requires permission, audit logged)
GET /api/v1/budget/cost-account/{id}/ledger           # Get detailed transaction ledger by account
POST /api/v1/budget/cost-account/{id}/sync            # Sync from source modules (settlement/invoice/payment)
```

**Dashboard Read Model**:
```
GET /api/v1/dashboard/project/{projectId}/cost-control   # Returns ProjectCostControlDTO:
{
  "projectId": 123,
  "projectName": "...",
  "wbsCount": 5,
  "accounts": [
    {"code": "01.01", "name": "Civil Works", "baseline": 1000000, "current": 1050000, "commitment": 900000, "actual": 450000, "forecast": 1100000, "variance": -50000}
  ],
  "totals": {...},
  "trends": [...]
}
```

### Change Events (`zw-contract`)

```
GET /api/v1/contract/change-event                     # List (filter: projectId, status, sourceType, startDate/endDate)
POST /api/v1/contract/change-event                    # Create draft event
GET /api/v1/contract/change-event/{id}                # Detail including workflow task state
PUT /api/v1/contract/change-event/{id}/assessment     # Submit impact assessment
POST /api/v1/contract/change-event/{id}/approvals     # Start approval (creates Flowable task)
GET /api/v1/contract/change-event/{id}/approvals      # List approval tasks
DELETE /api/v1/contract/change-event/{id}              # Cancel (draft only)
```

**Webhook Events** (when approved):
```yaml
change_event_approved:
  eventType: CHANGE_EVENT_APPROVED
  sourceUrl: https://api.zwinsight.com/api/v1/contract/change-event/{id}
  payload:
    id: 12345
    eventNumber: CHG-2026-001
    projectId: 789
    projectCode: ZW-2026-001
    deltaCost: 50000
    deltaScheduleDays: 5
    sourceType: FIELD_EVENT
    approvedAt: "2026-08-30T14:30:00Z"
    triggerActions:
      - type: BUDGET_UPDATE
        targetBudgetId: null # auto-create or link
      - type: CONTRACT_CHANGE_ORDER
        targetContractId: null
```

## Frontend Components

### PC Web

#### 1. Dashboard Redesign: Project Cost 360

**Location**: `zw-insight-web/src/views/dashboard/project-cost-control.vue`

**Layout**:
```
┌─────────────────────────────────────────────┐
│ Project Name  │ Filters: Period/Phase/CBS   │
├─────────────────────────────────────────────┤
│ KPI Cards (5)                               │
│ • Baseline Budget                           │
│ • Current Budget                            │
│ • Commitment (Pending Payment)              │
│ • Actual Cost                               │
│ • EAC (Forecast)                            │
├─────────────────────────────────────────────┤
│ Variance Trend Chart (Line/Area over time)  │
│ ─────────────────────────────────────────── │
│ Budget vs Actual Bar Chart                  │
├─────────────────────────────────────────────┤
│ CBS Tree Filter       │ Cost Account Table  │
│ • Expand/Collapse     │ • Columns: Code/Nam │
│ • Checkbox selection  │   e/Baseline/Curren │
│                     t │   t/Commitment/Ac/For │
└───────────────────────────────┬─────────────┘
                                ▼
                        ├─ Drawer Detail (CBS account)
                        │  └─ Transaction Ledger View
                        └─ Action Panel (Sync, Lock)
```

**Components**:
- `ProjectCostControl.vue`: Main page
- `KpiMetricCard.vue`: Reusable KPI component (value/subtitle/trend icon/color)
- `VarianceChart.vue`: ECharts Area chart with multiple lines (Baseline, Current, Actual, EAC)
- `CostLedgerTable.vue`: EL-Table with filters, grouping, sorting, frozen columns
- `CbsTreeFilter.vue`: Tree-select with checkboxes
- `CostAccountDrawer.vue`: Side panel showing account details + transactions

#### 2. Change Event Management

**Location**: `zw-insight-web/src/views/contract/change-event.vue`

**Pages**:
- `list.vue`: Grid with filters (status/category/date), quick action buttons (New, Export)
- `detail.vue`: Side drawer with tabs:
  - Info: metadata, status badge, workflow progress bar
  - Impact: editable JSON form for cost/schedule impact, attachment upload
  - Workflow: list of completed/approval tasks with history timeline
  - Audit: version history, who changed what
- `form.vue`: Modal for creating new draft event

**UX patterns**:
- Status badges with color coding (green/yellow/red/blue for different states)
- Inline edit for impact assessment (JSON field rendered as nice form)
- Approval flow visualization (similar to bpmn.js but simplified)
- Attachments mini-dialog (select/upload/reorder/delete)

### Mobile App

#### 1. Change Event Capture (现场变更事件采集)

**Location**: `zw-insight-app/src/pages/contract/change-event/index.vue`

**Design principles**:
- Quick capture: photo + text first, full form later
- Offline-first: save locally, sync when online
- Camera integration: support camera roll + direct capture

**UI Flow**:
```
1. List View (recent events, status chips)
   └── [+ New] FAB button bottom-right
       
2. Capture Screen (multi-step form):
   Step 1: Event Type (dropdown: FIELD_EVENT/DESIGN_CHANGE/etc.)
   Step 2: Photo Upload (camera/roll, max 5 photos, compression)
   Step 3: Description (text area, min 10 chars required)
   Step 4: Location (optional GPS, auto-detect from phone)
   └── Save Draft → creates LOCAL event → queued sync
   
3. Detail View (read-only except for adding photos/comments):
   • Header: event number, status badge, project selector
   • Timeline: created → submitted → approved/rejected (if visible)
   • Photos: carousel viewer with swipe + zoom
   • Actions: Edit Draft, Submit Assessment, Share, Download PDF
```

**Offline Support**:
- Use LocalStorage (via Vue Store plugin or localStorage wrapper)
- Queue sync events using background job (WebView + fetch)
- Conflict resolution: last-write-wins with server timestamp validation

## Testing Strategy

### Unit Tests (Frontend Vitest)

Target coverage: ≥80% for core logic functions.

#### Test Files to Create:

1. `src/views/dashboard/__tests__/project-cost-control.kpi-metric.test.ts`
   - KPI calculation formulas (margin rate, variance %, trend calc)
   
2. `src/components/CostLedgerTable.test.tsx`
   - Column rendering, filter state, pagination, row expansion
   
3. `src/views/contract/change-event/__tests__/impact-assessment-form.test.tsx`
   - Validation rules, json parsing, currency formatting
   
4. `src/composables/use-outbox-sync.test.ts`
   - Outbox enqueue/dequeue, retry logic, exponential backoff simulation

### Integration Tests (API Contract)

Use `keys/test-api-*` scripts pattern:
- `keys/test-api-change-event.sh`: verifies CRUD + status transitions
- `keys/test-api-cost-control.sh`: dashboard endpoint correctness

### Manual QA Checklist

1. Create WBS node → verify tree display
2. Link CBS account to WBS → verify linkage
3. Create Change Event → submit impact assessment → approve via Flowable → verify budget update
4. Submit material settlement → sync to CBS → verify actual cost increment
5. Mobile capture event offline → enable network → verify sync success

## Rollback Plan

All changes are additive or migration-safe:
- Tables added with soft delete flags (no DROP unless explicit rollback command)
- Existing budget detail records retain `cost_category`; we add CBS mapping layer without breaking
- Migration script includes `CREATE OR REPLACE VIEW vw_budget_legacy` for fallback queries
- CI check ensures no existing test regressions