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

## In-Progress Tasks
- Windows teammate verification on actual Windows remains in progress.
- Final GitHub push remains pending until final status/commit is prepared.

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
- Commit and push only intended source/docs changes.

## Bugs To Verify
- Manual negotiation should reject empty, non-number, over-budget, and invalid warranty inputs.
- Manual counter and walk-away should update the live feed.
- Automated negotiation should still update analytics and ledger.
- Auto Spawn should keep the current dashboard view and not interrupt further setup.
- Windows users should see readable controls, labels, charts, tables, and text fields.
- Confirm scroll behavior on smaller laptop screens and Windows display scaling.

## Final Testing Checklist
- Compile with JADE:
  - `javac -cp src/lib/jade.jar -d out/test-compile $(find src -name '*.java')`
- Launch `gui.MainDashboardFX`.
  - `java -cp out/test-compile:src/lib/jade.jar gui.MainDashboardFX`
- Start JADE Platform.
- Run Auto Spawn.
- Confirm broker receives dealer listings.
- Confirm buyers receive up to three matching dealers.
- Confirm automated negotiation produces live log and analytics updates.
- Confirm manual negotiation window appears and sends valid counter/walk-away messages.
- Confirm prediction advisor updates after negotiations.
- Confirm ledger and market report still work.
- Check smaller and larger dashboard sizes.
- Push only intended source/docs changes to GitHub.
