import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../core/api.service';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [CommonModule],
  template: `
    <h1>Reportes</h1>
    <p class="hint">
      Todo lo de esta pantalla se calcula desde el tópico <code>orders.events</code> de Kafka.
      El servicio de reportería nunca consulta la base de pedidos, así la analítica no bloquea el core.
    </p>

    <div class="metrics" *ngIf="lead() as l">
      <div class="metric"><b>{{ l.promedioMin }}</b><span>lead time promedio (min)</span></div>
      <div class="metric"><b>{{ l.medianaMin }}</b><span>mediana (min)</span></div>
      <div class="metric"><b>{{ l.maxMin }}</b><span>máximo (min)</span></div>
      <div class="metric"><b>{{ l.muestras }}</b><span>pedidos entregados</span></div>
    </div>

    <section class="panel">
      <h2>Ventas por hora</h2>
      <p class="empty" *ngIf="sales().length === 0">Sin datos todavía.</p>
      <div class="barras" *ngIf="sales().length">
        <div class="barra" *ngFor="let r of sales()">
          <div class="riel"><div class="relleno" [style.height.%]="alto(r.ventas)"></div></div>
          <span class="valor mono">{{ r.ventas | number }}</span>
          <span class="etiqueta">{{ r.hora.slice(-5) }}</span>
        </div>
      </div>
    </section>

    <section class="panel">
      <h2>Pedidos por estado</h2>
      <table>
        <tbody>
          <tr *ngFor="let e of states() | keyvalue">
            <td><span class="status" [attr.data-s]="e.key">{{ e.key }}</span></td>
            <td class="mono">{{ e.value }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="panel">
      <h2>Productos más vendidos</h2>
      <p class="empty" *ngIf="top().length === 0">Sin datos todavía.</p>
      <table *ngIf="top().length">
        <thead><tr><th>Producto</th><th>Unidades</th></tr></thead>
        <tbody>
          <tr *ngFor="let p of top()"><td class="mono">{{ p.productId }}</td><td class="mono">{{ p.unidades }}</td></tr>
        </tbody>
      </table>
    </section>
  `,
  styles: [`
    .metrics { margin-bottom: 1.5rem; }
    .barras { display: flex; gap: .6rem; align-items: flex-end; overflow-x: auto; padding-top: .5rem; }
    .barra { display: flex; flex-direction: column; align-items: center; gap: .3rem; min-width: 3.2rem; }
    .riel { width: 2rem; height: 7rem; background: #f0f3f7; border-radius: 3px;
      display: flex; align-items: flex-end; }
    .relleno { width: 100%; background: var(--signal); border-radius: 3px; min-height: 3px; }
    .valor { font-size: .72rem; }
    .etiqueta { font-size: .72rem; color: var(--muted); }
  `]
})
export class ReportsComponent implements OnInit {
  private api = inject(ApiService);
  sales = signal<any[]>([]);
  lead = signal<any>(null);
  states = signal<Record<string, number>>({});
  top = signal<any[]>([]);

  ngOnInit(): void {
    this.api.salesByHour().subscribe(d => this.sales.set(d));
    this.api.leadTime().subscribe(d => this.lead.set(d));
    this.api.activeStates().subscribe(d => this.states.set(d));
    this.api.topProducts().subscribe(d => this.top.set(d));
  }

  alto(v: number): number {
    const max = Math.max(...this.sales().map(s => s.ventas), 1);
    return Math.max(4, (v / max) * 100);
  }
}
