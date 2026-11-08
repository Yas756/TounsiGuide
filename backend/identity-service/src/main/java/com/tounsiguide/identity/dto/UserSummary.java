package com.tounsiguide.identity.dto;

import com.tounsiguide.identity.domain.User;

/** Profil minimal exposé aux autres utilisateurs authentifiés (ex. afficher l'auteur d'une publication). */
public record UserSummary(String id, String displayName) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getDisplayName());
    }
}
