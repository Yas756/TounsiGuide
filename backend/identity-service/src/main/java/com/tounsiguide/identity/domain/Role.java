package com.tounsiguide.identity.domain;

/**
 * Rôles globaux de la plateforme (portés par l'Identity Service).
 * Sprint 2 : MEMBER, ADMIN. Sprint 3 : ajouter REPRESENTATIVE ici (aucune migration de schéma nécessaire,
 * les rôles sont stockés sous forme de texte dans la table user_roles).
 * Les rôles propres à une page professionnelle restent gérés par le Business Service.
 */
public enum Role {
    MEMBER,
    ADMIN
}
