import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive, Router, NavigationEnd } from '@angular/router';
import { MsalService } from '@azure/msal-angular';
import { filter } from 'rxjs';
import { currentRoles } from './core/role.guard';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="layout" [class.bare]="bare()">
      <aside class="rail" *ngIf="!bare()">
        <div class="mark">
          <strong>Pedidos360</strong>
          <span>Consola de operaciones</span>
        </div>

        <nav>
          <a routerLink="/dashboard" routerLinkActive="on">Resumen</a>
          <a routerLink="/orders" routerLinkActive="on">Pedidos</a>
          <a routerLink="/catalog" routerLinkActive="on" *ngIf="can('Admin','Operador')">Catálogo</a>
          <a routerLink="/reports" routerLinkActive="on" *ngIf="can('Admin')">Reportes</a>
          <a routerLink="/audit" routerLinkActive="on" *ngIf="can('Admin','Auditor')">Auditoría</a>
          <a routerLink="/diagnostico" routerLinkActive="on">Diagnóstico</a>
        </nav>

        <div class="who" *ngIf="email()">
          <span class="name">{{ email() }}</span>
          <span class="role">{{ roles().join(' · ') || 'sin rol asignado' }}</span>
          <button type="button" class="salir" (click)="logout()">Cerrar sesión</button>
        </div>
      </aside>

      <main><router-outlet /></main>
    </div>
  `,
  styles: [`
    .layout { display: grid; grid-template-columns: 15rem 1fr; min-height: 100vh; }
    .layout.bare { grid-template-columns: 1fr; }

    .rail {
      background: var(--ink); color: #dfe5ec;
      display: flex; flex-direction: column; padding: 1.5rem 0;
      position: sticky; top: 0; height: 100vh;
    }
    .mark { padding: 0 1.4rem 1.75rem; }
    .mark strong { display: block; font-family: var(--display); font-weight: 800;
      font-size: 1.05rem; color: #fff; letter-spacing: -.01em; }
    .mark span { font-size: .78rem; color: #8d9cae; }

    nav { display: flex; flex-direction: column; }
    nav a {
      padding: .55rem 1.4rem; color: #b6c2d1; text-decoration: none; font-size: .92rem;
      border-left: 3px solid transparent;
    }
    nav a:hover { color: #fff; background: rgba(255,255,255,.05); }
    nav a.on { color: #fff; border-left-color: var(--signal); background: rgba(255,255,255,.07); font-weight: 500; }

    .who { margin-top: auto; padding: 1.25rem 1.4rem 0; border-top: 1px solid rgba(255,255,255,.1); }
    .name { display: block; font-size: .82rem; color: #dfe5ec; overflow-wrap: anywhere; }
    .role { display: block; font-size: .78rem; color: var(--signal); margin-bottom: .6rem; }
    .salir { background: none; border: 1px solid rgba(255,255,255,.2); color: #b6c2d1;
      padding: .3rem .6rem; border-radius: 4px; font-size: .8rem; cursor: pointer; }
    .salir:hover { color: #fff; border-color: rgba(255,255,255,.45); }

    main { padding: 2.25rem clamp(1.25rem, 3vw, 2.75rem); max-width: 68rem; }

    @media (max-width: 720px) {
      .layout { grid-template-columns: 1fr; }
      .rail { position: static; height: auto; }
      nav { flex-direction: row; overflow-x: auto; }
      nav a { border-left: none; border-bottom: 3px solid transparent; white-space: nowrap; }
      nav a.on { border-left: none; border-bottom-color: var(--signal); }
    }
  `]
})
export class AppComponent {
  private msal = inject(MsalService);
  private router = inject(Router);

  bare = signal(true);
  roles = signal<string[]>([]);
  email = signal('');

  constructor() {
    this.router.events.pipe(filter(e => e instanceof NavigationEnd))
      .subscribe(() => {
        const url = this.router.url;
        this.bare.set(url.startsWith('/login') || url.startsWith('/auth'));
        this.refresh();
      });
  }

  private refresh(): void {
    const acc = this.msal.instance.getActiveAccount() ?? this.msal.instance.getAllAccounts()[0];
    this.email.set(acc?.username ?? '');
    this.roles.set(currentRoles(this.msal));
  }

  can(...allowed: string[]): boolean {
    return this.roles().some(r => allowed.some(a => a.toLowerCase() === r.toLowerCase()));
  }

  logout(): void { this.msal.logoutRedirect(); }
}
