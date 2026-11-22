package com.tounsiguide.businessservice.rating;

import com.tounsiguide.businessservice.common.security.CurrentUser;
import com.tounsiguide.businessservice.common.security.ForbiddenException;
import com.tounsiguide.businessservice.page.PageAccess;
import com.tounsiguide.businessservice.rating.RatingDtos.RatingSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Star ratings of business pages (S3-US12): any logged-in member rates a verified page from 1 to 5, changes or
 * removes their rating; the members of the page cannot rate it. A rating is only an opinion: it never changes the
 * page, its wall or the general feed.
 */
@Service
public class RatingService {

    private final PageRatingRepository ratings;
    private final PageAccess access;

    public RatingService(PageRatingRepository ratings, PageAccess access) {
        this.ratings = ratings;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public RatingSummary summary(Long pageId, CurrentUser user) {
        access.activePage(pageId);
        return summaryOf(pageId, user);
    }

    @Transactional
    public RatingSummary rate(Long pageId, int stars, CurrentUser user) {
        access.activePage(pageId);
        if (access.isMember(pageId, user)) {
            throw new ForbiddenException("Les membres d'une page ne peuvent pas la noter");
        }
        ratings.findByPageIdAndUserId(pageId, user.id()).ifPresentOrElse(
                r -> r.change(stars, user.displayName()),
                () -> ratings.save(new PageRating(pageId, user.id(), user.displayName(), stars)));
        ratings.flush();
        return summaryOf(pageId, user);
    }

    @Transactional
    public RatingSummary remove(Long pageId, CurrentUser user) {
        access.activePage(pageId);
        ratings.findByPageIdAndUserId(pageId, user.id()).ifPresent(ratings::delete);
        ratings.flush();
        return summaryOf(pageId, user);
    }

    /** [average rounded to 0.1, count] for each page that has ratings (for page lists). */
    @Transactional(readOnly = true)
    public Map<Long, double[]> averages(Collection<Long> pageIds) {
        Map<Long, double[]> result = new HashMap<>();
        if (pageIds.isEmpty()) return result;
        for (Object[] row : ratings.averages(pageIds)) {
            result.put((Long) row[0], new double[]{round(((Number) row[1]).doubleValue()), ((Number) row[2]).doubleValue()});
        }
        return result;
    }

    private RatingSummary summaryOf(Long pageId, CurrentUser user) {
        long[] perStar = new long[5];
        for (Object[] row : ratings.distribution(pageId)) {
            int stars = ((Number) row[0]).intValue();
            if (stars >= 1 && stars <= 5) perStar[stars - 1] = ((Number) row[1]).longValue();
        }
        long count = 0;
        long total = 0;
        List<Long> distribution = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            count += perStar[i];
            total += perStar[i] * (i + 1);
            distribution.add(perStar[i]);
        }
        Double average = count == 0 ? null : round((double) total / count);
        Integer mine = user == null ? null
                : ratings.findByPageIdAndUserId(pageId, user.id()).map(PageRating::getStars).orElse(null);
        boolean canRate = user != null && !access.isMember(pageId, user);
        return new RatingSummary(pageId, average, count, distribution, mine, canRate);
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
