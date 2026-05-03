package analytics;

public final class NegotiationPredictor {
    public static final int DEFAULT_MAX_ROUNDS = 5;

    private NegotiationPredictor() {
    }

    public static Result predict(Input input) {
        double buyerOffer = positive(input.buyerOffer) ? input.buyerOffer : 0.0;
        double dealerAsk = positive(input.dealerAsk) ? input.dealerAsk : buyerOffer;
        double buyerBudget = positive(input.buyerBudget) ? input.buyerBudget : Math.max(dealerAsk, buyerOffer);
        double dealerReserve = positive(input.dealerReserve) ? input.dealerReserve : dealerAsk * 0.88;
        int maxRounds = input.maxRounds > 0 ? input.maxRounds : DEFAULT_MAX_ROUNDS;
        int round = Math.max(0, input.round);
        int remainingRounds = Math.max(0, maxRounds - round);
        String strategy = normalizeStrategy(input.dealerStrategy);

        if (Math.abs(buyerOffer - dealerAsk) < 0.01 && buyerOffer > 0) {
            return new Result(100.0, buyerOffer, input.dealerWarranty, "Deal closed", strategy,
                    "Final offer already matches dealer ask.", remainingRounds, 1.0);
        }

        double probability = baseProbability(strategy);
        double gap = Math.max(0.0, dealerAsk - buyerOffer);
        double denominator = Math.max(1.0, dealerAsk - dealerReserve);
        double reserveCloseness = 1.0 - clamp((dealerAsk - buyerOffer) / denominator, 0.0, 1.0);

        if (buyerOffer >= dealerReserve) {
            probability += reserveCloseness * 0.22;
        } else {
            double reserveMiss = clamp((dealerReserve - buyerOffer) / Math.max(1.0, dealerReserve), 0.0, 0.45);
            probability -= reserveMiss * 0.85;
        }

        if (buyerOffer >= dealerAsk) {
            probability += 0.18;
        }

        if (dealerAsk <= buyerBudget) {
            probability += 0.08;
        } else {
            double overBudget = clamp((dealerAsk - buyerBudget) / Math.max(1.0, buyerBudget), 0.0, 0.35);
            probability -= overBudget * 0.75;
        }

        double gapRatio = gap / Math.max(1.0, Math.max(dealerAsk, buyerBudget));
        probability -= gapRatio * 0.35;

        int warrantyGap = input.buyerWarranty - input.dealerWarranty;
        if (warrantyGap > 0) {
            probability -= Math.min(0.16, warrantyGap * 0.012);
        } else if (input.dealerWarranty > 0) {
            probability += 0.03;
        }

        double roundPressure = clamp(round / (double) maxRounds, 0.0, 1.0);
        if ("Desperate".equals(strategy)) {
            probability += roundPressure * 0.13;
        } else if ("Matcher".equals(strategy)) {
            probability += roundPressure * 0.07;
        } else {
            probability += roundPressure * 0.03;
        }

        if (remainingRounds == 0 && gap > 0.01) {
            probability -= 0.08;
        }

        if (positive(input.historicalAveragePrice)) {
            double historicalAverage = input.historicalAveragePrice;
            if (buyerOffer >= historicalAverage * 0.96) {
                probability += 0.05;
            }
            if (dealerAsk > historicalAverage * 1.08) {
                probability -= 0.05;
            }
        }

        probability = clamp(probability, 0.0, 1.0);

        double dealerWeight = dealerWeight(strategy);
        double predictedFinalPrice = (buyerOffer * (1.0 - dealerWeight)) + (dealerAsk * dealerWeight);
        if (positive(input.historicalAveragePrice)) {
            predictedFinalPrice = (predictedFinalPrice * 0.85) + (input.historicalAveragePrice * 0.15);
        }

        double lowerBound = Math.min(buyerOffer, dealerAsk);
        double upperBound = Math.max(buyerOffer, dealerAsk);
        if (buyerOffer < dealerReserve) {
            lowerBound = Math.min(dealerReserve, upperBound);
        }
        predictedFinalPrice = clamp(predictedFinalPrice, lowerBound, upperBound);

        int predictedWarranty = predictWarranty(input.buyerWarranty, input.dealerWarranty, strategy);
        String recommendedAction = recommendAction(probability, gapRatio, dealerAsk, buyerBudget, remainingRounds);
        String explanation = explain(probability, gap, dealerReserve, warrantyGap, strategy, remainingRounds);
        double recommendationScore = probability - (predictedFinalPrice / Math.max(1.0, buyerBudget) * 0.12);

        return new Result(probability * 100.0, predictedFinalPrice, predictedWarranty, recommendedAction,
                strategy, explanation, remainingRounds, recommendationScore);
    }

    public static String inferDealerStrategy(int round, double firstDealerAsk, double latestDealerAsk,
                                             double firstBuyerOffer, double latestBuyerOffer) {
        if (round <= 1 || !positive(firstDealerAsk) || !positive(latestDealerAsk)) {
            return "Matcher";
        }

        double dealerDrop = Math.max(0.0, firstDealerAsk - latestDealerAsk);
        double dealerDropRate = dealerDrop / Math.max(1.0, firstDealerAsk);
        double buyerMove = Math.max(0.0, latestBuyerOffer - firstBuyerOffer);
        double buyerMoveRate = buyerMove / Math.max(1.0, firstBuyerOffer);

        if (dealerDropRate < 0.035 && round >= 3) {
            return "Stubborn";
        }
        if (dealerDropRate > 0.09 || dealerDropRate > buyerMoveRate * 1.35) {
            return "Desperate";
        }
        return "Matcher";
    }

    public static String normalizeStrategy(String strategy) {
        if (strategy == null || strategy.trim().isEmpty()) {
            return "Matcher";
        }
        String trimmed = strategy.trim().toLowerCase();
        if (trimmed.contains("desperate")) {
            return "Desperate";
        }
        if (trimmed.contains("stubborn")) {
            return "Stubborn";
        }
        return "Matcher";
    }

    private static double baseProbability(String strategy) {
        if ("Desperate".equals(strategy)) {
            return 0.70;
        }
        if ("Stubborn".equals(strategy)) {
            return 0.35;
        }
        return 0.55;
    }

    private static double dealerWeight(String strategy) {
        if ("Stubborn".equals(strategy)) {
            return 0.72;
        }
        if ("Desperate".equals(strategy)) {
            return 0.35;
        }
        return 0.50;
    }

    private static int predictWarranty(int buyerWarranty, int dealerWarranty, String strategy) {
        if (buyerWarranty <= 0 && dealerWarranty <= 0) {
            return 0;
        }
        if (buyerWarranty <= dealerWarranty) {
            return dealerWarranty;
        }

        double concession = "Desperate".equals(strategy) ? 0.75 : "Matcher".equals(strategy) ? 0.50 : 0.25;
        int predicted = dealerWarranty + (int) Math.round((buyerWarranty - dealerWarranty) * concession);
        return Math.max(0, predicted);
    }

    private static String recommendAction(double probability, double gapRatio, double dealerAsk,
                                          double buyerBudget, int remainingRounds) {
        if (dealerAsk > buyerBudget && probability < 0.45) {
            return "Walk away";
        }
        if (probability >= 0.82 && dealerAsk <= buyerBudget && gapRatio <= 0.02) {
            return "Accept now";
        }
        if (probability >= 0.60 && gapRatio <= 0.055) {
            return "Counter slightly lower";
        }
        if (remainingRounds > 0 && probability >= 0.35) {
            return "Continue negotiation";
        }
        if (probability >= 0.55) {
            return "Counter slightly lower";
        }
        return "Walk away";
    }

    private static String explain(double probability, double gap, double dealerReserve,
                                  int warrantyGap, String strategy, int remainingRounds) {
        if (gap <= 0.01) {
            return "Buyer offer has met or exceeded the dealer ask.";
        }
        if (probability >= 0.75) {
            return "Offer is close to the dealer range and the " + strategy + " profile is likely to move.";
        }
        if (warrantyGap > 0) {
            return "Warranty request is above the dealer offer; price may need to compensate.";
        }
        if (remainingRounds == 0) {
            return "No negotiation rounds remain; only strong offers are likely to close.";
        }
        if (dealerReserve > 0 && gap > dealerReserve * 0.08) {
            return "Price gap is still large relative to the dealer reserve.";
        }
        return "Model balances price gap, reserve price, round pressure, and dealer profile.";
    }

    private static boolean positive(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value > 0.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class Input {
        public final String buyer;
        public final String dealer;
        public final String carModel;
        public final double buyerBudget;
        public final double buyerOffer;
        public final double dealerAsk;
        public final double dealerReserve;
        public final int buyerWarranty;
        public final int dealerWarranty;
        public final int round;
        public final int maxRounds;
        public final String dealerStrategy;
        public final double historicalAveragePrice;

        public Input(String buyer, String dealer, String carModel, double buyerBudget, double buyerOffer,
                     double dealerAsk, double dealerReserve, int buyerWarranty, int dealerWarranty,
                     int round, int maxRounds, String dealerStrategy, double historicalAveragePrice) {
            this.buyer = buyer;
            this.dealer = dealer;
            this.carModel = carModel;
            this.buyerBudget = buyerBudget;
            this.buyerOffer = buyerOffer;
            this.dealerAsk = dealerAsk;
            this.dealerReserve = dealerReserve;
            this.buyerWarranty = buyerWarranty;
            this.dealerWarranty = dealerWarranty;
            this.round = round;
            this.maxRounds = maxRounds;
            this.dealerStrategy = dealerStrategy;
            this.historicalAveragePrice = historicalAveragePrice;
        }
    }

    public static final class Result {
        public final double acceptanceProbabilityPercent;
        public final double predictedFinalPrice;
        public final int predictedWarrantyMonths;
        public final String recommendedAction;
        public final String dealerStrategy;
        public final String explanation;
        public final int remainingRounds;
        public final double recommendationScore;

        private Result(double acceptanceProbabilityPercent, double predictedFinalPrice,
                       int predictedWarrantyMonths, String recommendedAction, String dealerStrategy,
                       String explanation, int remainingRounds, double recommendationScore) {
            this.acceptanceProbabilityPercent = acceptanceProbabilityPercent;
            this.predictedFinalPrice = predictedFinalPrice;
            this.predictedWarrantyMonths = predictedWarrantyMonths;
            this.recommendedAction = recommendedAction;
            this.dealerStrategy = dealerStrategy;
            this.explanation = explanation;
            this.remainingRounds = remainingRounds;
            this.recommendationScore = recommendationScore;
        }
    }
}
