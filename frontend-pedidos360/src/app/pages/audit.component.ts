import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';

@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <h1>Auditoría</h1>
    <p class="hint">
      Registro de solo lectura construido desde el tópico <code>audit.timeline</code>.
      Cada línea conserva quién hizo qué y cuándo.
    </p>

    <section class="panel">
      <h2>Filtros</h2>
      <div class="form">
        <label class="field"><span>ID de pedido</span><input [(ngModel)]="entityId" name="entityId" placeholder="2a96be5e"></label>
        <label class="field"><span>Usuario (oid)</span><input [(ngModel)]="actor" name="actor"></label>
        <label class="field"><span>Tipo de evento</span>
          <select [(ngModel)]="tipo" name="tipo">
            <option value="">Todos</option>
            <option *ngFor="let t of tipos" [value]="t">{{ t }}</option>
          </select>
        </label>
        <button type="button" class="btn" (click)="buscar()">Buscar</button>
        <button type="button" class="btn btn-ghost" (click)="limpiar()">Limpiar</button>
      </div>
    </section>

    <section class="panel">
      <h2>Eventos <span class="cuenta">{{ events().length }}</span></h2>
      <p class="empty" *ngIf="events().length === 0">No hay eventos para ese filtro.</p>
      <ol class="linea">
        <li *ngFor="let e of events()">
          <span class="punto"></span>
          <div>
            <strong>{{ e.type }}</strong>
            <span class="meta">pedido <span class="mono">{{ (e.entityId || '').slice(0, 8) }}</span></span>
            <span class="meta">{{ e.occurredAt | date:'dd MMM yyyy, HH:mm:ss' }}</span>
            <span class="meta mono">{{ (e.actor || '').slice(0, 8) }}</span>
          </div>
        </li>
      </ol>
    </section>
  `,
  styles: [`
    .form { display: flex; gap: 1rem; align-items: flex-end; flex-wrap: wrap; }
    .cuenta { font-family: var(--body); font-size: .8rem; font-weight: 500; color: var(--muted); margin-left: .4rem; }
    .linea { list-style: none; margin: 0; padding: 0; }
    .linea li { position: relative; display: flex; gap: .9rem; padding: .7rem 0 .7rem 1.1rem;
      border-left: 2px solid var(--line); }
    .linea li:last-child { border-left-color: transparent; }
    .punto { position: absolute; left: -5px; top: 1.05rem; width: 8px; height: 8px;
      border-radius: 50%; background: var(--signal); }
    .meta { display: inline-block; font-size: .8rem; color: var(--muted); margin-left: .75rem; }
  `]
})
export class AuditComponent implements OnInit {
  private api = inject(ApiService);
  events = signal<any[]>([]);
  entityId = '';
  actor = '';
  tipo = '';

  tipos = ['OrderCreated', 'OrderAccepted', 'OrderPreparing', 'OrderDispatched', 'OrderDelivered', 'OrderCancelled'];

  ngOnInit(): void { this.buscar(); }

  limpiar(): void { this.entityId = ''; this.actor = ''; this.tipo = ''; this.buscar(); }

  buscar(): void {
    const params: Record<string, string> = {};
    if (this.entityId) params['entityId'] = this.entityId;
    if (this.actor) params['actor'] = this.actor;
    if (this.tipo) params['type'] = this.tipo;
    this.api.auditEvents(params).subscribe({
      next: d => this.events.set(d),
      error: () => this.events.set([])
    });
  }
}
