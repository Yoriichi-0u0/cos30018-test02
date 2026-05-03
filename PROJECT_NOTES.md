# COS30018 Automated Negotiation System Notes

## Current Project State
- Project: COS30018 Automated Negotiation System for automotive trading.
- UI stack: JavaFX dashboard with JADE agents.
- Current concept: broker-based automated vehicle negotiation platform.
- README summary: car dealers, car buying agents, and a broker agent negotiate vehicle trades.
- Assignment PDF requires a GUI, broker/dealer/buyer agents, manual negotiation, automated negotiation, implemented negotiation strategies, and implemented prediction algorithm/s.
- Latest implementation adds a visible rule-based prediction advisor in both analytics and manual negotiation.

## Main Architecture
- Broker Agent / KA: `src/agents/BrokerAgent.java`
  - Receives dealer listings.
  - Matches buyers to up to three relevant dealers based on budget.
  - Facilitates buyer shortlist handoff and tracks broker commission/fees.
- Dealer Agents / DAs: `src/agents/DealerAgent.java`
  - Register vehicle inventory with the broker.
  - Evaluate buyer first offers.
  - Initiate FIPA Contract Net negotiations.
- Buyer Agents / BAs: `src/agents/BuyerAgent.java`
  - Ask broker for matching dealers.
  - Send shortlist/first offers back through broker.
  - Run automated or manual negotiation mode.
- Ontology: `src/ontology/AuctionOntology.java`, `src/ontology/CarOffer.java`
  - Vehicle model, price, and warranty are exchanged as JADE content.
- GUI:
  - Main dashboard: `src/gui/MainDashboardFX.java`
  - Manual negotiation window: `src/gui/ManualUIFX.java`
  - Analytics and ledger: `src/gui/VisualAnalyticsFX.java`
- Config defaults: `src/config.properties`
  - Buyer default budget, dealer minimum price, dealer default warranty.

## Important UI Rules
- This repository uses JavaFX, not Swing.
- Keep JavaFX controls styled explicitly so text remains readable on Windows and macOS.
- Do not introduce JavaFX/Swing mixing.
- Dashboard should keep controls, live log, analytics charts, market chart, and ledger visible without relying on OS theme defaults.
- Manual negotiation controls must visibly affect logs and agent messages.
- The top control bar, analytics content, tables, live feed, and manual negotiation window are scrollable to avoid clipped controls on smaller displays.

## Important Compatibility Rules
- Compile/run requires JADE plus a JavaFX SDK/module path.
- Do not assume the plain Java command works unless JavaFX modules are available.
- Do not commit generated files, compiled classes, `.DS_Store`, or IDE workspace churn.

## Current Known Issues Before This Session
- `PROJECT_NOTES.md` and `TODO.md` did not exist.
- Prediction algorithm/s are not visible in the GUI even though the report requires them.
- Manual negotiation accepts weak/invalid inputs too easily.
- Windows teammate readability still needs verification on actual Windows machines after local styling changes.
- Existing worktree contains unrelated local/IDE files before this session: `.DS_Store`, `.idea/vcs.xml`, `untitled/untitled.iml`, `src/Main.java`, and `untitled/.DS_Store`.

## What Changed In This Session
- Added `src/analytics/NegotiationPredictor.java`, a reusable rule-based prediction model.
- Added a `Prediction Advisor` section to `src/gui/VisualAnalyticsFX.java`.
  - Selects negotiation sessions from a combo box.
  - Shows buyer, dealer, vehicle, current buyer offer, current dealer ask, acceptance chance, predicted final price, predicted warranty, recommended action, recommended dealer, and inferred dealer strategy.
  - Uses live negotiation snapshots and the ledger's historical average price for the selected vehicle when available.
- Added prediction guidance to `src/gui/ManualUIFX.java`.
  - The manual negotiation window recalculates acceptance chance and recommended action as the user edits price/warranty.
  - Added `Accept Dealer Offer` and stronger validation for empty, non-number, over-budget, and invalid warranty inputs.
  - If a manual buyer finds no matching dealer, an obvious friendly no-match window is shown and the live feed still logs the issue.
- Updated `src/agents/BuyerAgent.java` so manual no-match cases trigger the friendly Manual UI message.
- Updated `src/gui/MainDashboardFX.java` for cross-platform readability and scrollability.
  - Styled buttons through one helper.
  - Added explicit list cell colors for the live feed.
  - Added horizontal scrolling to the top controls.
  - Wrapped analytics content in a scroll pane.
  - Changed the window title to `COS30018 Broker Exchange - JavaFX`.

## Latest Fixes
- Prediction Advisor scroll fix:
  - `src/gui/VisualAnalyticsFX.java` now keeps the advisor shell compact and places prediction rows inside an internal `ScrollPane`.
  - The existing analytics-level scroll pane remains in `MainDashboardFX`, so smaller height windows can scroll both the whole analytics area and the prediction rows.
- Dynamic negotiation chart scaling:
  - Negotiation chart y-axis bounds now adjust per buyer/dealer session from observed buyer offers and dealer asks.
  - The old fixed RM90,000-RM135,000 range was removed so cheaper and more expensive vehicles remain readable.
- Warranty negotiation fix:
  - `src/agents/DealerAgent.java` now moves warranty gradually toward the buyer request by 2-4 months per counter.
  - Dealer warranty is capped at the dealer's max warranty threshold and clamped to 0-72 months.
  - Warranty surcharge is charged only for the additional warranty months actually added in that round.
  - Dealer no longer blindly increases warranty when the current warranty already satisfies the buyer request.
  - `src/agents/BuyerAgent.java` now reduces warranty demand gradually only when needed and never below the buyer's minimum acceptable warranty.
- Broker car-spec matching fix:
  - `BuyerAgent` sends the target vehicle and budget to `BrokerAgent` as `targetCar|budget`.
  - `BrokerAgent` logs the requested vehicle, matches exact/spec keywords first, and only then offers clearly logged budget-fit alternatives.
  - Broker still limits each buyer to at most three dealers.
- Manual prediction fix:
  - `DealerAgent` stamps CFP messages with negotiation round and inferred dealer strategy.
  - `BuyerAgent` passes those values into `ManualUIFX`.
  - `ManualUIFX` now predicts using the actual dealer, car, current buyer counter, dealer ask, requested warranty, dealer warranty, current round, and inferred strategy label.
- Buyer lock fix:
  - Auto buyers no longer set `lockedIn` when they merely propose to buy.
  - `lockedIn` is set only after JADE `handleAcceptProposal`, while `BrokerAgent.securedBuyers` remains the final anti-double-buying guard.
- Manual negotiation decision logic fix:
  - `DealerAgent` now uses explicit `ACCEPT`, `NEGOTIATE`, and `REJECT` outcomes for buyer proposals.
  - Acceptance considers buyer offer, current dealer ask, dealer reserve/min price, buyer warranty request, current dealer warranty, dealer max warranty, round number, and inferred dealer strategy.
  - Dealer can accept below current ask only when the offer is strategically acceptable after reserve, round pressure, and warranty compensation are considered.
  - Dealer rejects very low offers, impossible final-round warranty requests, locked buyers, and max-round failures.
  - Dealer logs walk-away/refuse responses and updates the negotiation chart with the last known buyer/dealer state.
  - `ManualUIFX` logs manual current-offer acceptance with the required wording.
  - `BuyerAgent` supports an optional second startup argument for demo/test buyer budget overrides; normal UI buttons still use `src/config.properties`.

## Prediction Algorithm Explanation
- The prediction model is rule-based and explainable, not machine learning.
- Inputs used:
  - Buyer current offer.
  - Buyer max budget from `src/config.properties`.
  - Dealer current asking price.
  - Dealer reserve/minimum price from `src/config.properties`.
  - Round number and remaining rounds.
  - Buyer requested warranty and dealer offered warranty.
  - Inferred dealer strategy: `Stubborn`, `Matcher`, or `Desperate`.
  - Historical average final price for the same vehicle from the current session ledger, when available.
- Acceptance probability:
  - Starts with a strategy base: Desperate 70%, Matcher 55%, Stubborn 35%.
  - Increases when the buyer offer is close to the dealer reserve/ask.
  - Increases when the dealer ask fits the buyer budget.
  - Adjusts for round pressure, warranty gap, and session historical price.
  - Decreases when offer is below reserve, ask exceeds budget, or the price gap is still large.
  - Clamped to 0-100%.
- Predicted final price:
  - Estimated between current buyer offer and dealer ask.
  - Stubborn dealers bias closer to dealer ask.
  - Desperate dealers bias closer to buyer offer.
  - Matcher dealers bias around the middle.
  - Current-session historical averages lightly influence the estimate if prior deals exist.
- Recommended action:
  - `Accept now`, `Counter slightly lower`, `Continue negotiation`, or `Walk away`, based on probability, budget fit, price gap, and remaining rounds.

## Key Decisions Made In This Session
- Treat the assignment PDF and this file as source of truth for this pass.
- Add an honest rule-based/data-derived prediction feature rather than fake static values.
- Use current negotiation rounds, current buyer offer, current dealer ask, warranty gap, configured budget/reserve price, inferred dealer behavior, and current ledger prices where available.
- Keep existing JADE message flow and class names.
- Improve manual negotiation validation while preserving its real JADE reply behavior.
- Do not introduce external dependencies.
- Keep the JavaFX app structure and launch class as `gui.MainDashboardFX`.

## Files Changed In This Session
- `PROJECT_NOTES.md`
- `TODO.md`
- `src/analytics/NegotiationPredictor.java`
- `src/agents/BrokerAgent.java`
- `src/agents/BuyerAgent.java`
- `src/agents/DealerAgent.java`
- `src/gui/MainDashboardFX.java`
- `src/gui/ManualUIFX.java`
- `src/gui/VisualAnalyticsFX.java`

## Testing Notes
- Compile passed:
  - `javac -cp src/lib/jade.jar -d out/test-compile $(find src -name '*.java')`
- Launch smoke check passed:
  - `java -cp out/test-compile:src/lib/jade.jar gui.MainDashboardFX`
- Automated GUI smoke runner passed locally:
  - Started JADE Platform.
  - Started Sniffer.
  - Ran Auto Demo.
  - Opened Market Report.
  - Spawned Manual Nego Platform.
  - Logs showed broker vehicle requests, budget-fit alternatives, successful deals, market report totals, and buyer lock after dealer acceptance.
  - Manual smoke interaction found 3 manual negotiation windows.
  - Submitted a 36-month manual counter; dealer warranty moved gradually from 12mo to 16mo and logged the adjustment.
  - Tested over-budget `Accept Dealer Offer` validation.
  - Tested `Walk Away` logging.
- Manual decision smoke runner passed locally:
  - Submitted an over-budget manual counter first and then a reasonable RM115,000 / 36mo counter.
  - Dealer responded with `NEGOTIATE`, gradually moving warranty from 6mo to 10mo and logging the counter.
  - Over-budget `Accept Dealer Offer` stayed blocked and logged clearly.
  - Walk-away sent `REFUSE`; dealer logged `[WALK AWAY]` and closed the negotiation without a deal.
  - Spawned a high-budget manual buyer through the optional budget argument.
  - `Accept Dealer Offer` within budget closed immediately, recorded a `WIN-WIN DEAL`, updated ledger/analytics, and appeared in Market Report.
- Launch produced JavaFX native-access warnings from the current JDK, but the dashboard process started successfully.
- GUI process was terminated after smoke testing.
- Windows teammate visual/readability verification is still required on an actual Windows machine.

## Known Limitations
- Dealer strategy is inferred from price movement because agents do not yet expose a named strategy profile.
- Budget-fit alternatives are clearly logged when exact/spec vehicle matches are unavailable; this keeps the demo active but should be explained in the report.
- Manual UI counter/accept/walk-away passed automated smoke interaction, but still needs human screenshots/video evidence for the report.
- Failure/walk-away cases are logged and charted, but there is no separate failed-negotiation KPI card yet.
- Windows display scaling/readability still needs teammate verification.
