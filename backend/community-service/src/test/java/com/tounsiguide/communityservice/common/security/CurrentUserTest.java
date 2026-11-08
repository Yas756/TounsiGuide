package com.tounsiguide.communityservice.common.security;

import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserTest {

    @Test
    void fromHeadersReadsIdNameAndRoles() {
        String encodedName = URLEncoder.encode("Sarra Ben Ahmed é", StandardCharsets.UTF_8);

        CurrentUser user = CurrentUser.fromHeaders("user-1", encodedName, "MEMBER, ADMIN");

        assertThat(user.id()).isEqualTo("user-1");
        assertThat(user.displayName()).isEqualTo("Sarra Ben Ahmed é");
        assertThat(user.roles()).containsExactlyInAnyOrder("MEMBER", "ADMIN");
        assertThat(user.isAdmin()).isTrue();
    }

    @Test
    void fromHeadersRejectsAMissingUserId() {
        assertThatThrownBy(() -> CurrentUser.fromHeaders(null, null, null))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> CurrentUser.fromHeaders("  ", null, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void fromHeadersUsesADefaultNameAndNoRoleWhenHeadersAreMissing() {
        CurrentUser user = CurrentUser.fromHeaders("user-1", null, null);

        assertThat(user.displayName()).isEqualTo("Membre");
        assertThat(user.roles()).isEmpty();
        assertThat(user.isAdmin()).isFalse();
    }

    @Test
    void onlyTheAuthorMayEdit() {
        CurrentUser author = CurrentUser.fromHeaders("user-1", null, "MEMBER");
        CurrentUser admin = CurrentUser.fromHeaders("admin-1", null, "MEMBER,ADMIN");

        author.requireAuthor("user-1");
        assertThatThrownBy(() -> admin.requireAuthor("user-1")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void theAuthorOrAnAdminMayDelete() {
        CurrentUser author = CurrentUser.fromHeaders("user-1", null, "MEMBER");
        CurrentUser admin = CurrentUser.fromHeaders("admin-1", null, "MEMBER,ADMIN");
        CurrentUser stranger = CurrentUser.fromHeaders("user-2", null, "MEMBER");

        author.requireAuthorOrAdmin("user-1");
        admin.requireAuthorOrAdmin("user-1");
        assertThatThrownBy(() -> stranger.requireAuthorOrAdmin("user-1"))
                .isInstanceOf(ForbiddenException.class);
    }
}
