# Identity Service — TounsiGuide

Service Spring Boot (Java 17, Spring Boot 3.3.5) : comptes, authentification JWT et rôles globaux.
Le contrat destiné à la Gateway, à Angular et aux autres services est dans [`AUTH_CONTRACT.md`](AUTH_CONTRACT.md).

## Lancer en local

```bash
# 1. Base MySQL
docker compose up -d

# 2. Variables d'environnement (valeurs de développement)
export JWT_SECRET="un-secret-d-au-moins-32-caracteres-........"
export ADMIN_EMAIL="admin@tounsiguide.local"     # optionnel : crée un admin au démarrage
export ADMIN_PASSWORD="ChangeMe12345"

# 3. Démarrer
mvn spring-boot:run
```

Le service écoute sur `http://localhost:8083` (variable `SERVER_PORT`). La base `tounsiguide_identity` est créée automatiquement.
Variables utiles : `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`.

## Essai rapide

```bash
curl -s -X POST localhost:8083/api/v1/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"sara@example.com","displayName":"Sara","password":"Password123"}'

curl -s localhost:8083/api/v1/users/me -H "Authorization: Bearer <accessToken>"
```

## Tests

```bash
mvn test
```

`AuthFlowTest` utilise H2 en mode MySQL (profil `test`) : inscription, doublon, validation, mauvais mot de passe,
accès protégé, 403 pour un membre, gestion des rôles par un admin, rotation et rejeu du refresh token, déconnexion.

## Structure

```
config/       SecurityConfig (JWT HS256, filtres), SecurityProperties, AdminBootstrap
controller/   AuthController, UserController, AdminUserController
service/      AuthService, UserService
security/     JwtService, TokenUtils (refresh token opaque + SHA-256)
domain/       User, Role, RefreshToken
repository/   UserRepository, RefreshTokenRepository
exception/    ApiException, ApiError, GlobalExceptionHandler
```

## Choix de conception

- Mots de passe hachés avec BCrypt ; message d'erreur identique pour email inconnu et mot de passe faux.
- Inscription publique = rôle `MEMBER` uniquement ; `ADMIN` s'attribue par un admin ou via l'admin initial.
- Refresh tokens stockés **hachés**, avec rotation à chaque usage et révocation de toutes les sessions en cas de rejeu.
- Rôles stockés en texte dans `user_roles` : ajouter `REPRESENTATIVE` au Sprint 3 = ajouter une valeur à l'enum `Role`.
- `ddl-auto: update` pour le prototype ; prévoir Flyway avant une mise en production.
