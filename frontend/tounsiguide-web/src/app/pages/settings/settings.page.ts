import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-settings-page',
  standalone: true,
  imports: [RouterLink],
  template: `
    <a class="link back" routerLink="/account">← Retour à mon compte</a>
    <section class="settings-heading">
      <div>
        <p class="eyebrow">Votre espace</p>
        <h1>Paramètres</h1>
        <p class="settings-lead muted">Les réglages de votre compte seront regroupés ici au même endroit.</p>
      </div>
      <span class="status status-proposed">En préparation</span>
    </section>

    <div class="settings-layout">
      <nav class="settings-nav" aria-label="Sections des paramètres">
        <span class="settings-nav-item active">Général</span>
        <span class="settings-nav-item">Profil</span>
        <span class="settings-nav-item">Sécurité</span>
        <span class="settings-nav-item">Notifications</span>
      </nav>
      <main class="settings-content">
        <section class="card settings-card">
          <span class="card-kicker">Général</span>
          <h2>Vos préférences, au même endroit.</h2>
          <p class="muted">Cette structure est prête pour les paramètres de profil, de sécurité et de notifications. Les contrôles seront activés lorsque les endpoints correspondants seront disponibles.</p>
          <div class="settings-preview-list">
            <div><span><strong>Informations du profil</strong><small class="muted">Nom affiché et identité publique</small></span><span class="status status-proposed">Bientôt</span></div>
            <div><span><strong>Sécurité du compte</strong><small class="muted">Mot de passe, sessions et connexion</small></span><span class="status status-proposed">Bientôt</span></div>
            <div><span><strong>Préférences de notification</strong><small class="muted">Choisir ce que vous souhaitez recevoir</small></span><span class="status status-proposed">Bientôt</span></div>
          </div>
        </section>
        <section class="settings-note">
          <strong>Votre compte fonctionne déjà.</strong>
          <span class="muted small">Pour le moment, vos informations et votre rôle restent accessibles depuis votre profil.</span>
          <a class="link" routerLink="/account">Voir mon profil →</a>
        </section>
      </main>
    </div>
  `,
})
export class SettingsPage {}
