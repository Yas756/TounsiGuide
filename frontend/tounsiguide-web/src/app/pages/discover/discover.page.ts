import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-discover-page',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="discover-hero" aria-labelledby="discover-title">
      <div class="discover-hero-copy">
        <p class="eyebrow">Le guide de la communauté</p>
        <h1 id="discover-title">Trouvez votre prochain bon plan en Tunisie.</h1>
        <p class="discover-lead">Des expériences partagées par des personnes qui connaissent vraiment les lieux, les habitudes et les détails qui comptent.</p>
        <div class="hero-actions">
          <a class="btn btn-primary" routerLink="/feed">Explorer le fil</a>
          <a class="btn btn-ghost" routerLink="/modules">Parcourir les modules</a>
        </div>
      </div>
    </section>

    <section class="discover-search-panel" aria-label="Recherche globale">
      <div class="search-heading">
        <span class="card-kicker">Commencer une recherche</span>
        <h2>Que cherchez-vous aujourd'hui ?</h2>
      </div>
      <div class="search-control" role="search">
        <span class="search-icon" aria-hidden="true">⌕</span>
        <input type="search" placeholder="Un café, une plage, une activité…" disabled aria-label="Recherche globale, bientôt disponible" />
        <span class="status status-proposed">Bientôt</span>
      </div>
      <p class="muted small">La recherche globale est en préparation. Vous pouvez déjà parcourir le fil général et les modules thématiques.</p>
    </section>

    <section class="discover-section" aria-labelledby="discover-paths-title">
      <div class="section-heading">
        <div>
          <p class="eyebrow">Choisir un point de départ</p>
          <h2 id="discover-paths-title">Explorez à votre rythme</h2>
        </div>
        <span class="muted small">Les parcours disponibles aujourd'hui</span>
      </div>
      <div class="discover-paths">
        <a class="path-card path-card-primary" routerLink="/feed">
          <span class="path-number">01</span>
          <span class="card-kicker">Communauté</span>
          <h3>Lire le fil général</h3>
          <p class="muted">Questions, recommandations et expériences partagées sans filtre artificiel.</p>
          <span class="link">Ouvrir le fil →</span>
        </a>
        <a class="path-card" routerLink="/modules">
          <span class="path-number">02</span>
          <span class="card-kicker">Organisation</span>
          <h3>Explorer les modules</h3>
          <p class="muted">Les espaces thématiques pour trouver plus vite ce qui vous intéresse.</p>
          <span class="link">Parcourir les modules →</span>
        </a>
        <a class="path-card" routerLink="/account">
          <span class="path-number">03</span>
          <span class="card-kicker">Votre espace</span>
          <h3>Gérer votre compte</h3>
          <p class="muted">Retrouvez votre profil, vos paramètres et les actions disponibles.</p>
          <span class="link">Ouvrir mon espace →</span>
        </a>
      </div>
    </section>

    <section class="discover-principles" aria-labelledby="principles-title">
      <div>
        <p class="eyebrow">Notre approche</p>
        <h2 id="principles-title">Découvrez plus facilement.</h2>
      </div>
      <div class="principles-list">
        <div><strong>01</strong><span><b>Partager librement.</b><br /><span class="muted">Un post peut exister avant d'avoir un lieu confirmé.</span></span></div>
        <div><strong>02</strong><span><b>Vérifier ensemble.</b><br /><span class="muted">Les associations et leurs preuves restent compréhensibles.</span></span></div>
        <div><strong>03</strong><span><b>Construire progressivement.</b><br /><span class="muted">Les modules sont là ; la recherche arrivera sans casser le fil.</span></span></div>
      </div>
    </section>
  `,
})
export class DiscoverPage {}
