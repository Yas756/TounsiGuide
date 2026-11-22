# Tests de bout en bout (S3-T01)

Le scénario de la démo du Sprint 3 (`sprint3/Sprint3_demo_and_restart.md`), joué automatiquement dans un vrai
navigateur, à travers la Gateway et les quatre services : 7 tests, environ 1 minute.

| Test | Étape de la démo |
|---|---|
| 1 | A (Sarra) suggère un module, l'admin l'approuve |
| 2-3 | A publie avec un lieu (« Plan B Coworking Café ») et le module ; puce du module dans le fil et dans la page du module ; lieu confirmé |
| 4 | B (Lina) demande la page officielle, l'admin approuve, B se reconnecte et a le badge « Représentant » |
| 5 | « Page vérifiée », le mur montre la publication plus ancienne de A, « Voir la page officielle » sur la publication |
| 6 | B modifie les infos, masque la publication du mur (elle reste dans le fil), publications masquées, historique, « Afficher à nouveau » |
| 7 | B signale la publication (« Spam ou publicité ») : elle reste visible |
| 8 | L'admin restreint : disparue du fil, du module et du mur, A voit « Publication restreinte par un modérateur » ; « Rétablir » |

## Une pile isolée : vos données ne sont jamais touchées

Les tests ne parlent **pas** aux services de tous les jours (8080-8084, 4200). Playwright démarre sa propre pile :

- les 5 services sur **9080-9084**, avec des bases **`tounsiguide_*_e2e`** effacées à chaque lancement
  (Flyway les recrée depuis `db/migration`), puis le jeu de test `docs/test-data/01_wipe_and_seed.sql` ;
- l'application Angular compilée avec la configuration `e2e` (Gateway 9080) sur **http://localhost:4300**.

Les deux piles peuvent tourner en même temps. Rien n'est à arrêter dans IntelliJ.

## Lancer

Prérequis : Java 21, Node 22, MySQL de WAMP démarré, Microsoft Edge (déjà sur Windows 11, aucun navigateur à télécharger).

```
cd e2e
npm install
npm run build-stack
npm test
```

- `npm run build-stack` : à relancer après chaque modification du code (backend ou Angular). Il compile une **copie**
  des services dans `e2e/.stack`, sans toucher aux dossiers `target/` utilisés par IntelliJ.
- `npm test` : démarre la pile, charge le jeu de test, joue les 7 tests, arrête tout.
- `npm run test:headed` : pareil, en voyant le navigateur.
- `npm run report` : rapport HTML ; en cas d'échec, capture d'écran et trace (`npx playwright show-trace …`).

Pour écrire ou déboguer un test sans redémarrer la pile à chaque fois :

```
npm run stack                       (terminal 1 : pile + jeu de test, Ctrl+C pour arrêter)
$env:E2E_REUSE=1; npx playwright test   (terminal 2, PowerShell ; les données sont rechargées à chaque lancement)
```

`npm run stack` sert aussi de bac à sable pour répéter la démo sur http://localhost:4300 sans abîmer vos vraies données.

## Réglages (variables d'environnement)

| Variable | Défaut |
|---|---|
| `MYSQL_BIN` | `C:/wamp64/bin/mysql/mysql8.4.7/bin` |
| `E2E_DB_USER` / `E2E_DB_PASSWORD` | `root` / vide |
| `E2E_BROWSER` | `msedge` (`chrome`, ou `chromium` après `npx playwright install chromium`) |
| `MVN` | le Maven déjà téléchargé dans `~/.m2/wrapper` (le `mvnw.cmd` du projet ne fonctionne pas quand le chemin contient une apostrophe, « Projet d'integration ») |
