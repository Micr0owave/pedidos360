import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MsalService } from '@azure/msal-angular';
import { Router } from '@angular/router';
import { environment } from '../../environments/environment';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="pantalla">
      <section class="acceso">
        <p class="marca">Pedidos360</p>
        <h1>La cocina, el reparto y la caja en un solo tablero.</h1>
        <p class="bajada">
          Ingresa con tu cuenta corporativa. Tu rol define qué pedidos ves y qué
          movimientos puedes hacer sobre ellos.
        </p>
        <button type="button" class="btn entrar" (click)="login()">
          Iniciar sesión con Microsoft
        </button>
        <p class="hint">Autenticación gestionada por Microsoft Entra ID.</p>
      </section>

      <aside class="riel" aria-hidden="true">
        <p class="titulo-riel">Ciclo de vida de un pedido</p>
        <ol>
          <li *ngFor="let e of etapas" [attr.data-s]="e.id">
            <span class="punto"></span>
            <span class="nombre">{{ e.nombre }}</span>
            <span class="nota">{{ e.nota }}</span>
          </li>
        </ol>
      </aside>
    </div>
  `,
  styles: [`
    .pantalla { display: grid; grid-template-columns: 1fr 20rem; min-height: 100vh; }
    .acceso { padding: clamp(2rem, 8vh, 6rem) clamp(1.5rem, 6vw, 5rem); align-self: center; }
    .marca { font-family: var(--display); font-weight: 800; font-size: .9rem;
      color: var(--signal); margin-bottom: 1.5rem; }
    h1 { font-size: clamp(1.8rem, 4vw, 2.6rem); max-width: 16ch; margin-bottom: 1rem; }
    .bajada { max-width: 42ch; color: var(--ink-soft); margin-bottom: 1.75rem; }
    .entrar { padding: .7rem 1.4rem; }
    .hint { margin-top: 1rem; }

    .riel { background: var(--ink); color: #c3cdd9; padding: 3rem 1.75rem; }
    .titulo-riel { font-size: .8rem; color: #8d9cae; margin-bottom: 1.5rem; }
    .riel ol { list-style: none; margin: 0; padding: 0; }
    .riel li { position: relative; padding: 0 0 1.5rem 1.6rem; border-left: 2px solid #2a3441; }
    .riel li:last-child { border-left-color: transparent; padding-bottom: 0; }
    .punto { position: absolute; left: -5px; top: .4rem; width: 8px; height: 8px;
      border-radius: 50%; background: #2a3441; }
    li[data-s="ENTREGADO"] .punto { background: #2f9c76; }
    li[data-s="ACEPTADO"] .punto, li[data-s="EN_PREPARACION"] .punto,
    li[data-s="DESPACHADO"] .punto { background: var(--signal); }
    .nombre { display: block; color: #fff; font-size: .9rem; font-weight: 500; }
    .nota { display: block; font-size: .78rem; color: #8d9cae; }

    @media (max-width: 800px) { .pantalla { grid-template-columns: 1fr; } .riel { display: none; } }
  `]
})
export class LoginComponent {
  private msal = inject(MsalService);
  private router = inject(Router);

  etapas = [
    { id: 'CREADO',         nombre: 'Creado',         nota: 'El cliente confirma su compra' },
    { id: 'ACEPTADO',       nombre: 'Aceptado',       nota: 'Se descuenta el stock' },
    { id: 'EN_PREPARACION', nombre: 'En preparación', nota: 'La cocina recibe el ticket' },
    { id: 'DESPACHADO',     nombre: 'Despachado',     nota: 'Sale a reparto' },
    { id: 'ENTREGADO',      nombre: 'Entregado',      nota: 'Cierra el lead time' }
  ];

  constructor() {
    if (this.msal.instance.getAllAccounts().length > 0) {
      this.router.navigate(['/dashboard']);
    }
  }

  login(): void {
    this.msal.loginRedirect({ scopes: [environment.apiScope] });
  }
}
