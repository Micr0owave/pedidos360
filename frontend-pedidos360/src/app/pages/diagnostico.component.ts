import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MsalService } from '@azure/msal-angular';
import { environment } from '../../environments/environment';

interface Chequeo { nombre: string; url: string; estado: 'pendiente' | 'ok' | 'falla'; detalle: string; }

/**
 * Pagina de diagnostico. Cumple dos funciones:
 *  1. Entrega el access_token en pantalla, con boton de copiar, para poder
 *     probar la API en Swagger, curl o el API Gateway sin pelear con la
 *     pestaña Network del navegador.
 *  2. Verifica de un vistazo que cada servicio responde, lo que sirve como
 *     evidencia durante la presentacion.
 */
@Component({
  selector: 'app-diagnostico',
  standalone: true,
  imports: [CommonModule],
  template: `
    <h1>Diagnóstico</h1>
    <p class="hint">
      Esta pantalla existe para la demostración: entrega el token vigente y confirma
      que cada pieza del sistema responde.
    </p>

    <section class="panel">
      <h2>Token de acceso</h2>
      <p class="hint" *ngIf="!token()">Solicitando token a Microsoft Entra ID…</p>

      <ng-container *ngIf="token() as t">
        <div class="acciones">
          <button type="button" class="btn btn-sm" (click)="copiar(t)">
            {{ copiado() ? 'Copiado' : 'Copiar token' }}
          </button>
          <button type="button" class="btn btn-ghost btn-sm" (click)="verEntero.set(!verEntero())">
            {{ verEntero() ? 'Ocultar' : 'Ver completo' }}
          </button>
          <button type="button" class="btn btn-ghost btn-sm" (click)="pedirToken()">Renovar</button>
        </div>
        <pre class="token" [class.corto]="!verEntero()">{{ t }}</pre>
      </ng-container>

      <h3 *ngIf="claims()">Contenido del token</h3>
      <table *ngIf="claims() as c">
        <tbody>
          <tr><td>Emisor (iss)</td><td class="mono">{{ c['iss'] }}</td></tr>
          <tr><td>Audiencia (aud)</td><td class="mono">{{ c['aud'] }}</td></tr>
          <tr><td>Versión (ver)</td>
              <td><span class="status" [attr.data-s]="esV2() ? 'ENTREGADO' : 'CANCELADO'">{{ c['ver'] }}</span></td></tr>
          <tr><td>Roles</td><td class="mono">{{ (c['roles'] || []).join(', ') || 'sin roles' }}</td></tr>
          <tr><td>Scope (scp)</td><td class="mono">{{ c['scp'] }}</td></tr>
          <tr><td>Expira</td><td class="mono">{{ expira() }}</td></tr>
        </tbody>
      </table>

      <div class="alert alert-error" *ngIf="claims() && !esV2()">
        El token es de versión 1.0. El caso exige issuer v2.0. Revisa
        <code>accessTokenAcceptedVersion</code> en el manifiesto de Pedidos360-API.
      </div>
    </section>

    <section class="panel">
      <h2>Estado de los servicios</h2>
      <button type="button" class="btn btn-ghost btn-sm" (click)="revisar()">Volver a probar</button>
      <table>
        <thead><tr><th>Servicio</th><th>Resultado</th><th>Detalle</th></tr></thead>
        <tbody>
          <tr *ngFor="let c of chequeos()">
            <td>{{ c.nombre }}<span class="ruta mono">{{ c.url }}</span></td>
            <td>
              <span class="status"
                    [attr.data-s]="c.estado === 'ok' ? 'ENTREGADO' : c.estado === 'falla' ? 'CANCELADO' : 'CREADO'">
                {{ c.estado === 'ok' ? 'Responde' : c.estado === 'falla' ? 'Sin respuesta' : 'Probando' }}
              </span>
            </td>
            <td class="mono detalle">{{ c.detalle }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  `,
  styles: [`
    .acciones { display: flex; gap: .5rem; margin-bottom: .75rem; flex-wrap: wrap; }
    .token { background: #f4f6f9; border: 1px solid var(--line); border-radius: var(--radius);
      padding: .7rem; font-family: ui-monospace, monospace; font-size: .74rem;
      overflow-wrap: anywhere; white-space: pre-wrap; margin: 0 0 1rem; }
    .token.corto { max-height: 5.5rem; overflow: hidden; }
    .ruta { display: block; font-size: .75rem; color: var(--muted); }
    .detalle { font-size: .8rem; color: var(--muted); }
  `]
})
export class DiagnosticoComponent implements OnInit {
  private msal = inject(MsalService);
  private http = inject(HttpClient);

  token = signal('');
  claims = signal<Record<string, any> | null>(null);
  copiado = signal(false);
  verEntero = signal(false);
  chequeos = signal<Chequeo[]>([]);

  ngOnInit(): void { this.pedirToken(); this.revisar(); }

  pedirToken(): void {
    const cuenta = this.msal.instance.getActiveAccount() ?? this.msal.instance.getAllAccounts()[0];
    if (!cuenta) { return; }
    this.msal.acquireTokenSilent({ scopes: [environment.apiScope], account: cuenta })
      .subscribe({
        next: r => { this.token.set(r.accessToken); this.claims.set(this.decodificar(r.accessToken)); },
        error: () => this.msal.acquireTokenRedirect({ scopes: [environment.apiScope] }).subscribe()
      });
  }

  copiar(t: string): void {
    navigator.clipboard.writeText(t).then(() => {
      this.copiado.set(true);
      setTimeout(() => this.copiado.set(false), 2000);
    });
  }

  esV2(): boolean {
    return this.claims()?.['ver'] === '2.0';
  }

  expira(): string {
    const exp = this.claims()?.['exp'];
    return exp ? new Date(exp * 1000).toLocaleString('es-CL') : '—';
  }

  private decodificar(t: string): Record<string, any> | null {
    try {
      let cuerpo = t.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      while (cuerpo.length % 4) { cuerpo += '='; }
      const bytes = Uint8Array.from(atob(cuerpo), ch => ch.charCodeAt(0));
      return JSON.parse(new TextDecoder('utf-8').decode(bytes));
    } catch { return null; }
  }

  revisar(): void {
    const base = environment.apiBaseUrl;
    const lista: Chequeo[] = [
      { nombre: 'BFF — ping autenticado',   url: `${base}/api/ping`,   estado: 'pendiente', detalle: '' },
      { nombre: 'BFF — identidad',      url: `${base}/api/me`,     estado: 'pendiente', detalle: '' },
      { nombre: 'Catálogo',             url: `${base}/api/catalog/products`, estado: 'pendiente', detalle: '' },
      { nombre: 'Pedidos',              url: `${base}/api/orders`, estado: 'pendiente', detalle: '' },
      { nombre: 'Reportes (Kafka)',     url: `${base}/api/report/kpi/active-states`, estado: 'pendiente', detalle: '' },
      { nombre: 'Auditoría (Kafka)',    url: `${base}/api/audit/events`, estado: 'pendiente', detalle: '' }
    ];
    this.chequeos.set(lista);

    lista.forEach((c, i) => {
      this.http.get(c.url, { responseType: 'text' }).subscribe({
        next: r => this.actualizar(i, 'ok', this.resumen(r)),
        error: e => this.actualizar(i, 'falla', `HTTP ${e.status || 0} ${e.statusText || ''}`.trim())
      });
    });
  }

  private resumen(r: string): string {
    const limpio = (r || '').trim();
    if (!limpio) return 'respuesta vacía';
    return limpio.length > 70 ? limpio.slice(0, 70) + '…' : limpio;
  }

  private actualizar(i: number, estado: Chequeo['estado'], detalle: string): void {
    const copia = [...this.chequeos()];
    copia[i] = { ...copia[i], estado, detalle };
    this.chequeos.set(copia);
  }
}
