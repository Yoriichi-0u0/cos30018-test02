# Automated Auto Auction System

An intelligent multi-agent platform for automated car negotiations using FIPA-compliant protocols. The system features autonomous Buyer, Dealer, and Broker agents that negotiate over price and warranty attributes.

## 🚀 Prerequisites

Before running the system, ensure you have the following installed:

1.  **Java Development Kit (JDK):** Version 11 or higher (Recommended: JDK 17+).
2.  **JavaFX SDK:** Required for the graphical user interface.
3.  **JADE (Java Agent DEvelopment Framework):** The project includes `jade.jar` in `src/lib/`.

## 🛠️ Setup & Configuration

### 1. Library Configuration
Ensure `src/lib/jade.jar` is added to your project's classpath or build path in your IDE (IntelliJ IDEA, Eclipse, etc.).

### 2. System Parameters
You can configure the default negotiation behavior in `src/config.properties`:
*   `buyer.default_budget`: Initial budget for car buyers.
*   `dealer.min_price`: The minimum price a dealer will accept.
*   `dealer.default_warranty`: Default warranty period offered.

## 🏃 How to Run

### Option 1: Using an IDE (Recommended)
1.  Open the project in your IDE.
2.  Locate `src/gui/MainDashboardFX.java`.
3.  Run the `main` method. This will launch the primary dashboard.

### Option 2: Command Line
Compile and run using the following (ensure paths are correct for your OS):
```powershell
# Compile
javac -cp ".;src;src/lib/jade.jar" src/gui/MainDashboardFX.java

# Run
java -cp ".;src;src/lib/jade.jar" gui.MainDashboardFX
```

## 🖥️ Operating the System

1.  **Launch Dashboard:** Upon starting `MainDashboardFX`, the central control panel will appear.
2.  **Initialize Agents:** Use the UI to spawn the Broker, followed by Dealers and Buyers.
3.  **Start Negotiation:** Trigger the auction process. You can monitor real-time message exchanges in the console and visual status updates in the UI.
4.  **View Analytics:** After negotiations conclude, use the Analytics panel to review price trends and negotiation outcomes.

## 📂 Project Structure
*   `src/agents/`: Logic for Broker, Buyer, and Dealer agents.
*   `src/gui/`: JavaFX-based user interfaces.
*   `src/ontology/`: FIPA-compatible communication schemas.
*   `src/lib/`: External dependencies (JADE).
