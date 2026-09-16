import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MsalService } from '@azure/msal-angular';
import { ApiService, Order } from '../core/api.service';
import { currentRoles } from '../core/role.guard';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <h1>{{ saludo() }}</h1>
    <p class="hint">{{ contexto() }}</p>

    <div class="metrics">
      <div class="metric"><b>{{ orders().length }}</b><span>pedidos visibles</span></div>
      <div class="metric"><b>{{ enCurso() }}</b><span>en curso</span></div>
      <div class="metric"><b>{{ entregados() }}</b><span>entregados</span></div>
      <div class="metric"><b>{{ ventas() | number }}</b><span>total acumulado</span></div>
    </div>

    <section class="panel">
      <h2>Actividad reciente</h2>
      <p class="empty" *ngIf="cargando()">Cargando…</p>
      <p class="empty" *ngIf="!cargando() && orders().length === 0">
        Sin pedidos todavía. <a routerLink="/orders">Crear el primero</a>.
      </p>
      <table *ngIf="orders().length">
        <thead><tr><th>Pedido</th><th>Estado</th><th>Total</th><th>Creado</th></tr></thead>
        <tbody>
          <tr *ngFor="let o of recientes()">
            <td class="mono">{{ o.id.slice(0, 8) }}</td>
            <td><span class="status" [attr.data-s]="o.status">{{ o.status }}</span></td>
            <td class="mono">{{ o.total | number }}</td>
            <td class="hint">{{ o.createdAt | date:'dd MMM, HH:mm' }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  `,
  styles: [`.metrics { margin-bottom: 1.5rem; }`]
})
export class DashboardComponent implements OnInit {
  private api = inject(ApiService);
  private msal = inject(MsalService);

  orders = signal<Order[]>([]);
  cargando = signal(true);
  roles = signal<string[]>([]);
  nombre = signal('');

  recientes = computed(() => this.orders().slice(0, 10));
  enCurso = computed(() => this.orders().filter(o =>
    !['ENTREGADO', 'CANCELADO'].includes(o.status)).length);
  entregados = computed(() => this.orders().filter(o => o.status === 'ENTREGADO').length);
  ventas = computed(() => this.orders().reduce((s, o) => s + (o.total ?? 0), 0));

  saludo = computed(() => {
    const n = this.nombre().split(/[@.]/)[0];
    return n ? `Hola, ${n.charAt(0).toUpperCase() + n.slice(1)}` : 'Resumen';
  });

  contexto = computed(() => {
    const r = this.roles().map(x => x.toLowerCase());
    if (r.includes('admin')) return 'Como administrador ves todos los pedidos, los KPIs y el registro de auditoría.';
    if (r.includes('operador')) return 'Como operador puedes aceptar, preparar y despachar los pedidos en curso.';
    if (r.includes('auditor')) return 'Como auditor tienes acceso de solo lectura al registro de eventos.';
    if (r.includes('cliente')) return 'Aquí ves el estado de los pedidos que has creado.';
    return 'Tu cuenta no tiene un rol asignado en Pedidos360. Pídele a un administrador que te lo asigne.';
  });

  ngOnInit(): void {
    this.roles.set(currentRoles(this.msal));
    const acc = this.msal.instance.getActiveAccount() ?? this.msal.instance.getAllAccounts()[0];
    this.nombre.set(acc?.username ?? '');

    this.api.listOrders().subscribe({
      next: d => { this.orders.set(d); this.cargando.set(false); },
      error: () => this.cargando.set(false)
    });
  }
}
