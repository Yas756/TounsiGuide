package com.tounsiguide.identity.domain;

/**
 * Rôles globaux de la plateforme (portés par l'Identity Service).
 * Sprint 2 : MEMBER, ADMIN. Sprint 3 : BUSINESS_REP (aucune migration de schéma nécessaire,
 * les rôles sont stockés sous forme de texte dans la table user_roles).
 * BUSINESS_REP est ajouté par le Business Service quand une demande de page est approuvée
 * (POST /internal/users/{id}/roles/BUSINESS_REP). Le droit d'agir sur UNE page précise reste décidé par le
 * Business Service (membres de la page), jamais par ce rôle seul.
 */
public enum Role {
    MEMBER,
    ADMIN,
    BUSINESS_REP
}
