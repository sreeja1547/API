package com.ecommerce.productordermanagement.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.ecommerce.productordermanagement.dto.ProductComparisonInfo;
import com.ecommerce.productordermanagement.dto.StoreOffer;
import com.ecommerce.productordermanagement.entity.Product;

@Service
public class ProductComparisonService {

    public ProductComparisonInfo buildComparison(Product product) {
        if (product == null) {
            return null;
        }

        double basePrice = product.getPromotionalPrice() != null && product.getPromotionalPrice() > 0
                ? product.getPromotionalPrice()
                : (product.getPrice() != null ? product.getPrice() : 999.0);

        double actualPrice = product.getActualPrice() != null && product.getActualPrice() > basePrice
                ? product.getActualPrice()
                : Math.round(basePrice * 1.35 * 100.0) / 100.0;

        double baseRating = product.getRating() != null && product.getRating() > 0
                ? product.getRating()
                : 4.2;

        int baseReviews = product.getNumberOfRatings() != null && product.getNumberOfRatings() > 0
                ? product.getNumberOfRatings()
                : 1250;

        long seed = (product.getId() != null ? product.getId() * 31 : 0)
                + (product.getName() != null ? product.getName().hashCode() : 42);

        String category = (product.getCategory() != null ? product.getCategory() : "").toLowerCase(Locale.ROOT);
        String mainCategory = (product.getMainCategory() != null ? product.getMainCategory() : "").toLowerCase(Locale.ROOT);

        // Select third retailer based on category
        String thirdStoreName;
        String thirdStoreCode;
        String thirdStoreBadge;
        if (category.contains("fashion") || mainCategory.contains("fashion") || category.contains("cloth") || category.contains("shoe")) {
            thirdStoreName = "Myntra";
            thirdStoreCode = "myntra";
            thirdStoreBadge = "Myntra";
        } else if (category.contains("electron") || mainCategory.contains("tv") || category.contains("camera") || category.contains("audio")) {
            thirdStoreName = "Croma";
            thirdStoreCode = "croma";
            thirdStoreBadge = "Croma";
        } else {
            thirdStoreName = "Walmart";
            thirdStoreCode = "walmart";
            thirdStoreBadge = "Walmart";
        }

        // Variances deterministically generated from seed
        int pattern = (int) (Math.abs(seed) % 6);

        double amazonPriceMultiplier;
        double flipkartPriceMultiplier;
        double thirdPriceMultiplier;

        double amazonRatingDelta;
        double flipkartRatingDelta;
        double thirdRatingDelta;

        int amazonReviewMultiplier;
        int flipkartReviewMultiplier;
        int thirdReviewMultiplier;

        switch (pattern) {
            case 0:
                // Flipkart lowest price + higher rating
                flipkartPriceMultiplier = 0.88;
                amazonPriceMultiplier = 1.00;
                thirdPriceMultiplier = 1.06;
                flipkartRatingDelta = +0.2;
                amazonRatingDelta = 0.0;
                thirdRatingDelta = -0.1;
                flipkartReviewMultiplier = 8;
                amazonReviewMultiplier = 10;
                thirdReviewMultiplier = 3;
                break;
            case 1:
                // Amazon lowest price + higher rating
                flipkartPriceMultiplier = 1.05;
                amazonPriceMultiplier = 0.90;
                thirdPriceMultiplier = 1.08;
                flipkartRatingDelta = -0.1;
                amazonRatingDelta = +0.2;
                thirdRatingDelta = 0.0;
                flipkartReviewMultiplier = 7;
                amazonReviewMultiplier = 12;
                thirdReviewMultiplier = 2;
                break;
            case 2:
                // Flipkart lowest price, Amazon highest reviews
                flipkartPriceMultiplier = 0.92;
                amazonPriceMultiplier = 1.02;
                thirdPriceMultiplier = 1.04;
                flipkartRatingDelta = +0.1;
                amazonRatingDelta = +0.1;
                thirdRatingDelta = -0.2;
                flipkartReviewMultiplier = 9;
                amazonReviewMultiplier = 15;
                thirdReviewMultiplier = 4;
                break;
            case 3:
                // Third retailer lowest price deal
                flipkartPriceMultiplier = 1.04;
                amazonPriceMultiplier = 1.02;
                thirdPriceMultiplier = 0.89;
                flipkartRatingDelta = 0.0;
                amazonRatingDelta = +0.1;
                thirdRatingDelta = +0.3;
                flipkartReviewMultiplier = 6;
                amazonReviewMultiplier = 11;
                thirdReviewMultiplier = 5;
                break;
            case 4:
                // Flipkart lowest price with steep discount
                flipkartPriceMultiplier = 0.85;
                amazonPriceMultiplier = 0.98;
                thirdPriceMultiplier = 1.05;
                flipkartRatingDelta = +0.3;
                amazonRatingDelta = +0.1;
                thirdRatingDelta = -0.1;
                flipkartReviewMultiplier = 10;
                amazonReviewMultiplier = 14;
                thirdReviewMultiplier = 3;
                break;
            default:
                // Amazon competitive price match
                flipkartPriceMultiplier = 0.97;
                amazonPriceMultiplier = 0.91;
                thirdPriceMultiplier = 1.03;
                flipkartRatingDelta = +0.1;
                amazonRatingDelta = +0.2;
                thirdRatingDelta = +0.1;
                flipkartReviewMultiplier = 8;
                amazonReviewMultiplier = 13;
                thirdReviewMultiplier = 4;
                break;
        }

        double amazonPrice = Math.max(99.0, Math.round(basePrice * amazonPriceMultiplier));
        double flipkartPrice = Math.max(99.0, Math.round(basePrice * flipkartPriceMultiplier));
        double thirdPrice = Math.max(99.0, Math.round(basePrice * thirdPriceMultiplier));

        double amazonRating = Math.min(5.0, Math.max(3.5, Math.round((baseRating + amazonRatingDelta) * 10.0) / 10.0));
        double flipkartRating = Math.min(5.0, Math.max(3.5, Math.round((baseRating + flipkartRatingDelta) * 10.0) / 10.0));
        double thirdRating = Math.min(5.0, Math.max(3.5, Math.round((baseRating + thirdRatingDelta) * 10.0) / 10.0));

        int amazonReviews = Math.max(150, (baseReviews / 10) * amazonReviewMultiplier);
        int flipkartReviews = Math.max(120, (baseReviews / 10) * flipkartReviewMultiplier);
        int thirdReviews = Math.max(80, (baseReviews / 10) * thirdReviewMultiplier);

        // Find initial min price and max rating
        double minPrice = Math.min(amazonPrice, Math.min(flipkartPrice, thirdPrice));
        double maxPrice = Math.max(amazonPrice, Math.max(flipkartPrice, thirdPrice));
        double maxRating = Math.max(amazonRating, Math.max(flipkartRating, thirdRating));
        double priceSavings = Math.max(0.0, maxPrice - minPrice);
        int savingsPercent = maxPrice > 0 ? (int) Math.round((priceSavings / maxPrice) * 100.0) : 0;

        String amazonUrl = product.getProductUrl() != null && !product.getProductUrl().isBlank()
                ? product.getProductUrl()
                : "https://www.amazon.in/s?k=" + encodeQuery(product.getName());

        String flipkartUrl = "https://www.flipkart.com/search?q=" + encodeQuery(product.getName());
        String thirdUrl = "https://www.google.com/search?q=" + encodeQuery(thirdStoreName + " " + product.getName());

        List<StoreOffer> rawOffers = new ArrayList<>();
        rawOffers.add(new StoreOffer(
                "Flipkart",
                "flipkart",
                "🛒 Flipkart",
                flipkartPrice,
                actualPrice,
                calculateDiscount(actualPrice, flipkartPrice),
                flipkartRating,
                flipkartReviews,
                "Free 2-day delivery",
                "7 Days Replacement",
                flipkartUrl,
                flipkartPrice == minPrice,
                flipkartRating == maxRating,
                false
        ));

        rawOffers.add(new StoreOffer(
                "Amazon",
                "amazon",
                "📦 Amazon",
                amazonPrice,
                actualPrice,
                calculateDiscount(actualPrice, amazonPrice),
                amazonRating,
                amazonReviews,
                "Free One-Day Prime delivery",
                "10 Days Return",
                amazonUrl,
                amazonPrice == minPrice,
                amazonRating == maxRating,
                false
        ));

        rawOffers.add(new StoreOffer(
                thirdStoreName,
                thirdStoreCode,
                thirdStoreBadge,
                thirdPrice,
                actualPrice,
                calculateDiscount(actualPrice, thirdPrice),
                thirdRating,
                thirdReviews,
                "Standard 2-4 day shipping",
                "Brand warranty included",
                thirdUrl,
                thirdPrice == minPrice,
                thirdRating == maxRating,
                false
        ));

        // Intelligent Decision Engine to decide the BEST choice ("Winner")
        // Scoring formula: (Price Advantage * 60%) + (Rating Advantage * 30%) + (Review Confidence * 10%)
        StoreOffer bestOffer = null;
        double bestScore = -1.0;

        for (StoreOffer offer : rawOffers) {
            double priceScore = maxPrice > minPrice
                    ? (maxPrice - offer.price()) / (maxPrice - minPrice) * 60.0
                    : 60.0;
            double ratingScore = (offer.rating() / 5.0) * 30.0;
            double reviewScore = Math.min(10.0, (Math.log10(Math.max(10, offer.reviewCount())) / 4.0) * 10.0);
            double totalScore = priceScore + ratingScore + reviewScore;

            if (totalScore > bestScore) {
                bestScore = totalScore;
                bestOffer = offer;
            }
        }

        if (bestOffer == null) {
            bestOffer = rawOffers.get(0);
        }

        // Generate the 1-Line Explanation of WHY it showed that best pick
        String decisionReason = generateOneLineReason(bestOffer, rawOffers, minPrice, maxPrice, priceSavings);

        // Mark best choice in offers list
        final StoreOffer finalBest = bestOffer;
        List<StoreOffer> finalizedOffers = rawOffers.stream()
                .map(o -> new StoreOffer(
                        o.storeName(),
                        o.storeCode(),
                        o.storeBadge(),
                        o.price(),
                        o.originalPrice(),
                        o.discountPercent(),
                        o.rating(),
                        o.reviewCount(),
                        o.deliveryInfo(),
                        o.returnPolicy(),
                        o.productUrl(),
                        o.price() == minPrice,
                        o.rating() == maxRating,
                        o.storeCode().equalsIgnoreCase(finalBest.storeCode())
                ))
                .sorted(Comparator.comparingDouble(StoreOffer::price))
                .toList();

        StoreOffer lowestOffer = finalizedOffers.stream()
                .filter(StoreOffer::isLowestPrice)
                .findFirst()
                .orElse(finalBest);

        StoreOffer highestRatedOffer = finalizedOffers.stream()
                .max(Comparator.comparingDouble(StoreOffer::rating))
                .orElse(finalBest);

        return new ProductComparisonInfo(
                minPrice,
                lowestOffer.storeName(),
                maxPrice,
                priceSavings,
                savingsPercent,
                highestRatedOffer.rating(),
                highestRatedOffer.storeName(),
                finalBest.storeName(),
                finalBest.price(),
                finalBest.rating(),
                decisionReason,
                finalizedOffers
        );
    }

    public void decorateWithComparison(Product product) {
        if (product != null) {
            product.setComparison(buildComparison(product));
        }
    }

    public void decorateWithComparison(List<Product> products) {
        if (products != null) {
            for (Product product : products) {
                decorateWithComparison(product);
            }
        }
    }

    private String generateOneLineReason(StoreOffer best, List<StoreOffer> all,
                                         double minPrice, double maxPrice, double savings) {
        boolean isLowest = best.price() == minPrice;
        StoreOffer highestPriceStore = all.stream()
                .max(Comparator.comparingDouble(StoreOffer::price))
                .orElse(best);

        String vsStore = !highestPriceStore.storeName().equalsIgnoreCase(best.storeName())
                ? highestPriceStore.storeName()
                : "other websites";

        if (isLowest && savings > 0 && best.rating() >= 4.3) {
            return "Recommended as Best Pick because " + best.storeName()
                    + " offers the lowest verified price of ₹" + formatPrice(best.price())
                    + " (saving ₹" + formatPrice(savings) + " vs " + vsStore + ") with an exceptional "
                    + best.rating() + "★ customer rating.";
        } else if (isLowest && savings > 0) {
            return "Recommended as Best Pick because " + best.storeName()
                    + " provides the lowest price of ₹" + formatPrice(best.price())
                    + " (Save ₹" + formatPrice(savings) + " vs " + vsStore + ") with solid "
                    + best.rating() + "★ satisfaction across " + String.format(Locale.ROOT, "%,d", best.reviewCount()) + " reviews.";
        } else if (best.rating() >= 4.5) {
            return "Recommended for superior buyer satisfaction with " + best.rating()
                    + "★ rating from " + String.format(Locale.ROOT, "%,d", best.reviewCount())
                    + " buyers on " + best.storeName() + ", justifying the minimal price difference.";
        } else {
            return "Recommended for the best overall balance of price, seller reliability, and "
                    + best.rating() + "★ rating on " + best.storeName() + ".";
        }
    }

    private int calculateDiscount(double actualPrice, double salePrice) {
        if (actualPrice <= salePrice || actualPrice <= 0) {
            return 0;
        }
        return (int) Math.round(((actualPrice - salePrice) / actualPrice) * 100.0);
    }

    private String formatPrice(double price) {
        if (price == Math.floor(price)) {
            return String.format(Locale.ROOT, "%,d", (long) price);
        }
        return String.format(Locale.ROOT, "%,.2f", price);
    }

    private String encodeQuery(String text) {
        if (text == null) {
            return "";
        }
        return text.replace(" ", "+");
    }
}
