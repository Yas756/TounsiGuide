package com.tounsiguide.businessservice.rating;

import com.tounsiguide.businessservice.common.security.CurrentUser;
import com.tounsiguide.businessservice.common.security.ForbiddenException;
import com.tounsiguide.businessservice.page.PageAccess;
import com.tounsiguide.businessservice.rating.RatingDtos.RatingSummary;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    private static final CurrentUser SARRA = new CurrentUser("user-1", "Sarra", Set.of("MEMBER"));

    @Mock
    private PageRatingRepository ratings;
    @Mock
    private PageAccess access;
    @InjectMocks
    private RatingService service;

    @Test
    void aMemberRatesAPageAndGetsTheSummary() {
        when(access.isMember(5L, SARRA)).thenReturn(false);
        when(ratings.findByPageIdAndUserId(5L, "user-1")).thenReturn(Optional.empty(), Optional.of(new PageRating(5L, "user-1", "Sarra", 4)));
        when(ratings.distribution(5L)).thenReturn(List.of(new Object[]{4, 1L}, new Object[]{5, 2L}));

        RatingSummary summary = service.rate(5L, 4, SARRA);

        verify(ratings).save(any(PageRating.class));
        assertThat(summary.count()).isEqualTo(3);
        assertThat(summary.average()).isEqualTo(4.7); // (4 + 5 + 5) / 3 = 4.67
        assertThat(summary.distribution()).containsExactly(0L, 0L, 0L, 1L, 2L);
        assertThat(summary.myStars()).isEqualTo(4);
        assertThat(summary.canRate()).isTrue();
    }

    @Test
    void ratingAgainReplacesTheStars() {
        PageRating existing = new PageRating(5L, "user-1", "Sarra", 2);
        when(access.isMember(5L, SARRA)).thenReturn(false);
        when(ratings.findByPageIdAndUserId(5L, "user-1")).thenReturn(Optional.of(existing));
        when(ratings.distribution(5L)).thenReturn(List.<Object[]>of(new Object[]{5, 1L}));

        service.rate(5L, 5, SARRA);

        assertThat(existing.getStars()).isEqualTo(5);
        verify(ratings, never()).save(any());
    }

    @Test
    void theMembersOfAPageCannotRateIt() {
        when(access.isMember(5L, SARRA)).thenReturn(true);

        assertThatThrownBy(() -> service.rate(5L, 5, SARRA)).isInstanceOf(ForbiddenException.class);
        verify(ratings, never()).save(any());
    }

    @Test
    void anUnknownPageIs404() {
        when(access.activePage(99L)).thenThrow(new EntityNotFoundException("Page introuvable"));

        assertThatThrownBy(() -> service.rate(99L, 3, SARRA)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void aVisitorSeesTheAverageButCannotRate() {
        when(ratings.distribution(5L)).thenReturn(List.<Object[]>of(new Object[]{3, 2L}));

        RatingSummary summary = service.summary(5L, null);

        assertThat(summary.average()).isEqualTo(3.0);
        assertThat(summary.myStars()).isNull();
        assertThat(summary.canRate()).isFalse();
    }

    @Test
    void aPageWithoutRatingsHasNoAverage() {
        when(ratings.distribution(5L)).thenReturn(List.of());

        RatingSummary summary = service.summary(5L, null);

        assertThat(summary.average()).isNull();
        assertThat(summary.count()).isZero();
    }

    @Test
    void removingTheRatingDeletesIt() {
        PageRating existing = new PageRating(5L, "user-1", "Sarra", 2);
        when(ratings.findByPageIdAndUserId(5L, "user-1")).thenReturn(Optional.of(existing), Optional.empty());
        when(ratings.distribution(5L)).thenReturn(List.of());

        RatingSummary summary = service.remove(5L, SARRA);

        verify(ratings).delete(existing);
        assertThat(summary.myStars()).isNull();
    }
}
