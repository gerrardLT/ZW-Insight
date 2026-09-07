# Cost Control Backbone - Requirements

## Problem Statement

Current system has functional ERP capabilities but lacks the unified "Construction Operating System" backbone:
- **No CBS/WBS hierarchy**: Budget details are flat by cost category (MATERIAL/LABOR/etc.), no work breakdown or cost account structure linking budget → contract → actual cost
- **No Change Event domain**: Field changes (签证/变更) go directly to ChangeVisa without systematic impact assessment feeding both budget change AND contract change
- **Weak cross-module consistency**: No domain event/outbox infrastructure; modules call each other's services directly, risking data inconsistency
- **Limited cost visibility**: ProjectDashboard shows budget vs paid payments only; no Commitment, No Forecast/EAC, no Variance by CBS dimension

## Goal

Transform into a Construction Operating System with unified cost control backbone:
**Project → WBS/CBS → Budget Baseline → Change Event → Current Budget → Commitment → Actual Cost → Forecast (EAC) → Variance**

## Success Criteria

1. **Data Consistency**: Budget changes driven by approved Change Events with documented impact assessment
2. **Cost Visibility**: Dashboard shows Baseline / Current / Commitment / Actual / Forecast / Variance by CBS account
3. **Change Traceability**: Field Change Event → Impact Assessment → Approval → Drives Budget Change + Contract Change + Revenue Change
4. **Modular Integrity**: Domain events via Transactional Outbox for eventual consistency across modules

## Non-Goals (This Phase)

- Full Primavera Unifier-style multi-level CBS/WBS trees (we'll do 2 levels max for MVP)
- Advanced forecasting algorithms (EAC = AC + (BAC - EV)/CPI etc.) — just simple EAC = AC + remaining estimate
- Procurement three-way match automation (PO/Receipt/Invoice matching rules) — separate phase
