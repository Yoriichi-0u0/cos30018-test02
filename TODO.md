# COS30018 Automated Negotiation System TODO

## Completed Tasks
- Read README, assignment PDF, project structure, GUI classes, agent classes, ontology classes, and config defaults.
- Created project memory files for future Codex sessions.
- Added reusable rule-based prediction model.
- Added Visual Analytics `Prediction Advisor`.
- Added Manual Negotiation prediction guidance.
- Added manual input validation for empty price, non-number price, over-budget price, and invalid warranty.
- Added manual `Accept Dealer Offer` flow.
- Added friendly manual no-match dialog.
- Improved dashboard/readability styling for buttons and live feed cells.
- Added scroll panes for the top controls, analytics area, and manual negotiation window.
- Verified compile.
- Verified GUI launch smoke check.
- Fixed Prediction Advisor row clipping with an internal scroll pane.
- Fixed dynamic negotiation chart y-axis scaling.
- Fixed dealer warranty negotiation to move gradually and cap at dealer max warranty.
- Fixed buyer warranty negotiation so demand does not fall below minimum acceptable warranty.
- Fixed broker matching to include buyer target vehicle specification plus budget.
- Fixed manual prediction to use round and inferred strategy from the active CFP.
- Fixed auto buyer lock lifecycle so buyers lock only after dealer acceptance.
- Ran an automated GUI smoke runner for Start JADE, Start Sniffer, Auto Demo, Market Report, and Manual Nego Platform.
- Ran manual interaction smoke for 36-month counter, over-budget accept validation, and walk-away logging.
- Improved dealer manual/auto decision logic with explicit accept/negotiate/reject outcomes.
- Verified manual `Accept Dealer Offer` within budget closes a deal and updates Market Report.
- Verified manual walk-away/refuse is logged by DealerAgent.
- Verified over-budget manual counter/accept paths are blocked or logged clearly.

## In-Progress Tasks
- Windows teammate verification on actual Windows remains in progress.

## Remaining Tasks
- Capture screenshots/video evidence for the report:
  - Broker receiving dealer listings.
  - Broker matching buyer requests to up to three dealers.
  - Automated negotiation chart updates.
  - Manual negotiation window with prediction advisor.
  - Prediction Advisor in Visual Analytics.
  - Ledger and market report after successful deals.
- Explain the prediction formula in the report under `Implemented prediction algorithm/s`.
- Explain the current strategy behavior/inference in the report under `Implemented negotiation strategies`.
- Explain budget-fit alternative matching as a fallback after exact/spec matching.
- Explain manual dealer decision thresholds for accept/negotiate/reject.

## Bugs To Verify
- Manual negotiation should reject empty, non-number, over-budget, and invalid warranty inputs.
- Manual counter and walk-away should update the live feed.
- Automated negotiation should still update analytics and ledger.
- Auto Spawn should keep the current dashboard view and not interrupt further setup.
- Windows users should see readable controls, labels, charts, tables, and text fields.
- Confirm scroll behavior on smaller laptop screens and Windows display scaling.
- Capture human screenshots/video for `Submit Counter`, `Accept Dealer Offer`, and `Walk Away`.
- Consider adding a separate failed/walk-away KPI if the report needs numeric failure counts.

## Final Testing Checklist
- Compile with JADE:
  - `javac -cp src/lib/jade.jar -d out/test-compile $(find src -name '*.java')`
- Launch `gui.MainDashboardFX`.
  - `java -cp out/test-compile:src/lib/jade.jar gui.MainDashboardFX`
- Automated smoke passed locally for:
  - Start JADE Platform.
  - Start Sniffer.
  - Run Auto Demo.
  - Market Report.
  - Manual Nego Platform.
  - Broker target car request logging.
  - Successful auto deals and ledger/market report activity.
  - Manual over-budget counter validation.
  - Manual reasonable counter negotiation.
  - Manual over-budget accept validation.
  - Manual walk-away/refuse logging.
  - Manual accept-within-budget deal closure.
- Manual remaining checks:
  - Confirm Prediction Advisor internal scroll by resizing the analytics area.
  - Check smaller and larger dashboard sizes.
