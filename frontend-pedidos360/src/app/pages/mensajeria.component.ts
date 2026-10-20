import { Component, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService, MqEvent, MqScenario, PeekedMessage, QueueSummary } from '../core/api.service';

@Component({
  selector: 'app-mensajeria',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <h1>Mensajería</h1>
    <p class="hint">
      Estado de RabbitMQ y de los consumidores, sin abrir la consola. Se actualiza cada 3 segundos.
    </p>

    <div class="alert alert-error" *ngIf="error()">{{ error() }}</div>
    <div class="alert alert-ok" *ngIf="info()">{{ info() }}</div>

    <section class="panel">
      <h2>Probar escenarios</h2>
      <p class="hint">Envía un comando de prueba y mira abajo cómo reacciona el consumidor.</p>
      <div class="row">
        <label class="field"><span>Cola de destino</span>
          <select [(ngModel)]="destino" name="destino">
            <option value="email">Correo (q.cmd.email)</option>
            <option value="kitchen">Cocina (q.cmd.kitchen)</option>
            <option value="invoice">Boleta (q.cmd.invoice)</option>
          </select>
        </label>
        <button type="button" class="btn" (click)="probar('ok')">Mensaje correcto</button>
        <button type="button" class="btn btn-ghost" (click)="probar('invalid')">Inválido → DLQ</button>
        <button type="button" class="btn btn-ghost" (click)="probar('transient')">Error temporal → reintentos</button>
        <button type="button" class="btn btn-ghost" (click)="probar('duplicate')">Duplicado</button>
      </div>
    </section>

    <section class="panel">
      <h2>Colas <span class="cuenta">{{ colas().length }}</span></h2>
      <p class="empty" *ngIf="colas().length === 0">Sin datos de RabbitMQ todavía.</p>
      <table *ngIf="colas().length > 0">
        <thead><tr><th>Cola</th><th>Mensajes</th><th>Listos</th><th>Sin confirmar</th><th>Consumidores</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let q of colas()" [class.muerta]="q.name.endsWith('.dlq') && q.messages > 0">
            <td class="mono">{{ q.name }} <span class="tag" *ngIf="q.deadLetter">con DLQ</span></td>
            <td>{{ q.messages }}</td><td>{{ q.ready }}</td><td>{{ q.unacked }}</td><td>{{ q.consumers }}</td>
            <td class="acciones">
              <button type="button" class="btn btn-ghost btn-sm" (click)="ver(q.name)">Ver mensajes</button>
              <button type="button" class="btn btn-ghost btn-sm" *ngIf="borrable(q.name)" (click)="borrar(q.name)">Eliminar</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="hint">Las colas del sistema (<code>q.cmd.*</code>) no se pueden eliminar desde aquí.</p>
    </section>

    <section class="panel" *ngIf="seleccion()">
      <h2>Mensajes en <span class="mono">{{ seleccion() }}</span></h2>
      <p class="empty" *ngIf="mensajes().length === 0">La cola está vacía.</p>
      <div class="msg" *ngFor="let m of mensajes()">
        <span class="meta">routing key <code>{{ m.routingKey }}</code><span *ngIf="m.redelivered"> · reentregado</span></span>
        <pre>{{ m.payload }}</pre>
      </div>
    </section>

    <section class="panel">
      <h2>Eventos del consumidor <span class="cuenta">{{ eventos().length }}</span></h2>
      <p class="empty" *ngIf="eventos().length === 0">Aún no hay eventos. Envía un mensaje de prueba.</p>
      <table *ngIf="eventos().length > 0">
        <thead><tr><th>Hora</th><th>Cola</th><th>Resultado</th><th>Detalle</th><th>Correlación</th></tr></thead>
        <tbody>
          <tr *ngFor="let e of eventos()">
            <td>{{ e.time | date:'HH:mm:ss' }}</td>
            <td class="mono">{{ e.queue }}</td>
            <td><span class="res" [attr.data-r]="e.result">{{ e.result }}</span></td>
            <td>{{ e.reason }}</td>
            <td class="mono">{{ e.correlationId }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="panel">
      <h2>Crear cola</h2>
      <form class="row" (ngSubmit)="crear()">
        <label class="field"><span>Nombre</span><input [(ngModel)]="nueva" name="nueva" placeholder="q.demo.prueba"></label>
        <button type="submit" class="btn" [disabled]="!nueva.trim()">Crear</button>
      </form>
    </section>
  `,
  styles: [`
    .row { display: flex; gap: .75rem; align-items: flex-end; flex-wrap: wrap; }
    .cuenta { font-family: var(--body); font-size: .8rem; font-weight: 500; color: var(--muted); margin-left: .4rem; }
    .acciones { display: flex; gap: .4rem; justify-content: flex-end; }
    .tag { font-size: .7rem; color: var(--muted); border: 1px solid var(--line); border-radius: 3px; padding: 0 .3rem; margin-left: .4rem; }
    tr.muerta td { background: var(--clay-bg); }
    .msg { border-top: 1px solid var(--line); padding: .6rem 0; }
    .meta { font-size: .8rem; color: var(--muted); }
    pre { margin: .3rem 0 0; white-space: pre-wrap; overflow-wrap: anywhere; font-size: .8rem; }
    .res { display: inline-block; padding: .15rem .5rem; border-radius: 3px; font-size: .75rem; font-weight: 600; }
    .res[data-r="ACK"] { background: var(--jade-bg); color: var(--jade); }
    .res[data-r="DUPLICADO"] { background: var(--steel-bg); color: var(--steel); }
    .res[data-r="REINTENTO"] { background: var(--signal-bg); color: var(--signal); }
    .res[data-r^="DLQ"] { background: var(--clay-bg); color: var(--clay); }
  `]
})
export class MensajeriaComponent implements OnInit, OnDestroy {
  private api = inject(ApiService);
  private timer?: ReturnType<typeof setInterval>;

  colas = signal<QueueSummary[]>([]);
  eventos = signal<MqEvent[]>([]);
  mensajes = signal<PeekedMessage[]>([]);
  seleccion = signal('');
  error = signal('');
  info = signal('');

  destino = 'email';
  nueva = '';

  ngOnInit(): void {
    this.refrescar();
    this.timer = setInterval(() => this.refrescar(), 3000);
  }

  ngOnDestroy(): void { clearInterval(this.timer); }

  borrable(name: string): boolean { return !name.startsWith('q.cmd.'); }

  refrescar(): void {
    this.api.mqQueues().subscribe({
      next: d => { this.colas.set(d); this.error.set(''); },
      error: e => this.fallo('No se pudo consultar RabbitMQ', e)
    });
    this.api.mqEvents().subscribe({ next: d => this.eventos.set(d), error: () => { /* el aviso ya lo da mqQueues */ } });
    if (this.seleccion()) this.cargarMensajes();
  }

  probar(modo: MqScenario): void {
    this.api.mqTest(this.destino, modo).subscribe({
      next: () => { this.info.set(`Enviado: ${modo} → ${this.destino}`); setTimeout(() => this.refrescar(), 1200); },
      error: e => this.fallo('No se pudo enviar el mensaje de prueba', e)
    });
  }

  ver(name: string): void { this.seleccion.set(name); this.cargarMensajes(); }

  private cargarMensajes(): void {
    this.api.mqPeek(this.seleccion()).subscribe({
      next: d => this.mensajes.set(d),
      error: e => this.fallo('No se pudieron leer los mensajes', e)
    });
  }

  crear(): void {
    const name = this.nueva.trim();
    this.api.mqCreateQueue(name).subscribe({
      next: () => { this.info.set(`Cola creada: ${name}`); this.nueva = ''; this.refrescar(); },
      error: e => this.fallo('No se pudo crear la cola', e)
    });
  }

  borrar(name: string): void {
    if (!confirm(`¿Eliminar la cola ${name}?`)) return;
    this.api.mqDeleteQueue(name).subscribe({
      next: () => { this.info.set(`Cola eliminada: ${name}`); if (this.seleccion() === name) this.seleccion.set(''); this.refrescar(); },
      error: e => this.fallo('No se pudo eliminar la cola', e)
    });
  }

  private fallo(msg: string, e: HttpErrorResponse): void {
    this.info.set('');
    this.error.set(`${msg}${e.error?.detail ? ': ' + e.error.detail : e.status ? ` (HTTP ${e.status})` : ''}`);
  }
}
