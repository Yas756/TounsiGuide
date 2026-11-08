# Contrat d'authentification TounsiGuide

Document destiné à l'équipe **Gateway** et à l'équipe **Angular**, ainsi qu'aux services Community, Location et Business.
Propriétaire : Identity Service (port par défaut `8081`).

## 1. Principe

1. Angular appelle `POST /api/v1/auth/login` (via la Gateway) et reçoit un **access token JWT** (15 min) et un **refresh token** opaque (7 jours).
2. Angular envoie ensuite `Authorization: Bearer <accessToken>` sur chaque requête protégée.
3. **La Gateway valide le JWT** (signature, expiration, issuer), puis transmet l'identité aux services en aval via des en-têtes.
4. **Chaque service propriétaire vérifie les droits métier** (ex. « seul l'auteur modifie sa publication »). La Gateway ne remplace pas ces contrôles.

## 2. Format du JWT (HS256)

| Élément | Valeur |
|---|---|
| Algorithme | `HS256` |
| Secret | variable d'environnement `JWT_SECRET` (≥ 32 caractères), **identique** dans l'Identity Service et la Gateway. Ne jamais le committer. |
| `iss` | `tounsiguide-identity` |
| `sub` | identifiant de l'utilisateur (UUID, chaîne de 36 caractères) |
| `email` | email normalisé en minuscules |
| `name` | nom affiché |
| `roles` | tableau de chaînes, ex. `["MEMBER"]` ou `["MEMBER","ADMIN"]` |
| `iat` / `exp` | émission / expiration (15 min) |

Les rôles globaux sont `MEMBER` et `ADMIN` (Sprint 2). `REPRESENTATIVE` sera ajouté au Sprint 3. Les rôles propres à une page professionnelle restent gérés par le Business Service.

## 3. Ce que la Gateway doit faire

- Laisser passer sans token : `POST /api/v1/auth/register`, `/api/v1/auth/login`, `/api/v1/auth/refresh`, `/api/v1/auth/logout`, ainsi que les routes publiques (ex. lecture du fil).
- Pour toute route protégée : valider le JWT, sinon répondre `401`.
- **Supprimer** les en-têtes `X-User-Id` et `X-User-Roles` envoyés par le client (anti-usurpation), puis les remplacer par :
  - `X-User-Id: <sub>`
  - `X-User-Roles: MEMBER,ADMIN` (liste séparée par des virgules)
- Les services en aval ne doivent **pas** être joignables directement depuis l'extérieur : seule la Gateway l'est.

Alternative possible pour les services : transmettre le JWT tel quel et le valider avec le même secret. Choix à confirmer avec l'équipe ; les en-têtes sont l'option la plus simple.

## 4. Endpoints de l'Identity Service

| Méthode | Route | Accès | Rôle |
|---|---|---|---|
| POST | `/api/v1/auth/register` | public | Crée un compte `MEMBER` et renvoie les tokens (201) |
| POST | `/api/v1/auth/login` | public | Renvoie les tokens (200) |
| POST | `/api/v1/auth/refresh` | public | Échange un refresh token contre une nouvelle paire (rotation) |
| POST | `/api/v1/auth/logout` | public | Révoque le refresh token (204) |
| GET | `/api/v1/users/me` | authentifié | Profil complet de l'appelant |
| GET | `/api/v1/users/{id}` | authentifié | Profil minimal `{id, displayName}` (afficher l'auteur) |
| GET | `/api/v1/admin/users?page=0&size=20` | `ADMIN` | Liste paginée des utilisateurs |
| PUT | `/api/v1/admin/users/{id}/roles` | `ADMIN` | Remplace les rôles (`MEMBER` toujours conservé) |

### Exemples

`POST /api/v1/auth/register`
```json
{ "email": "sara@example.com", "displayName": "Sara", "password": "Password123" }
```

Réponse `201` / `200` (register, login, refresh) :
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "q3Zx...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": { "id": "…", "email": "sara@example.com", "displayName": "Sara",
            "roles": ["MEMBER"], "createdAt": "2026-11-07T16:00:00Z" }
}
```

`POST /api/v1/auth/refresh` et `POST /api/v1/auth/logout` : `{ "refreshToken": "q3Zx..." }`

### Format d'erreur

```json
{ "timestamp": "…", "status": 400, "error": "Bad Request", "message": "Données invalides",
  "path": "/api/v1/auth/register", "details": { "email": "Format d'email invalide" } }
```

Codes utilisés : `400` validation, `401` non authentifié ou identifiants incorrects, `403` rôle insuffisant, `404` ressource inconnue, `409` email déjà utilisé.

## 5. Consignes pour Angular (S2-F06, S2-F07)

- Conserver l'access token en mémoire, le refresh token dans un stockage adapté ; ajouter un intercepteur HTTP qui pose `Authorization: Bearer …`.
- Sur un `401`, tenter **une** fois `POST /api/v1/auth/refresh`, puis rejouer la requête ; en cas d'échec, rediriger vers la connexion.
- Le refresh token change à chaque usage : toujours remplacer l'ancien par le nouveau. Réutiliser un ancien token révoque toutes les sessions de l'utilisateur.
- Masquer/afficher les boutons d'après `user.roles` pour le confort d'usage uniquement : le backend reste l'autorité.

## 6. Consignes pour les services Community / Location / Business

- Lire l'identité dans `X-User-Id` / `X-User-Roles` (jamais dans le corps de la requête).
- Stocker l'identifiant d'auteur comme chaîne (UUID) ; ne pas copier l'email.
- Vérifier la propriété de la ressource et les rôles métier dans le service propriétaire.
- Au Sprint 1, les données de démonstration peuvent utiliser des `userId` factices au même format (UUID) afin que le branchement au Sprint 2 ne casse rien.

## 7. Limites connues

- Un changement de rôle n'est visible qu'après expiration de l'access token en cours (≤ 15 min) ou nouvelle connexion.
- Pas encore : vérification d'email, réinitialisation de mot de passe, limitation de tentatives de connexion, désactivation de compte par l'admin (le champ `enabled` existe déjà).
