import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, Order, OrderStatus, Product } from '../core/api.service';

@Component({
  selector: 'app-orders',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <h1>Pedidos</h1>
    <p class="hint">
      Cada movimiento se valida contra la máquina de estados en el servidor.
      Los destinos bloqueados aparecen tachados y se pueden intentar igual, para demostrar el rechazo.
    </p>

    <div class="alert alert-error" *ngIf="error()">{{ error() }}</div>
    <div class="alert alert-ok" *ngIf="ok()">{{ ok() }}</div>

    <section class="panel">
      <h2>Nuevo pedido</h2>
      <div class="form">
        <label class="field">
          <span>Producto</span>
          <select [(ngModel)]="productId" name="productId">
            <option *ngFor="let p of products()" [value]="p.id">
              {{ p.name }} — {{ p.price | number }} ({{ p.stock }} en stock)
            </option>
          </select>
        </label>
        <label class="field">
          <span>Cantidad</span>
          <input type="number" min="1" [(ngModel)]="quantity" name="quantity" style="width:6rem">
        </label>
        <label class="field">
          <span>Correo del cliente</span>
          <input type="email" [(ngModel)]="customerEmail" name="customerEmail" placeholder="cliente@duocuc.cl">
        </label>
        <button type="button" class="btn" (click)="create()" [disabled]="creando()">
          {{ creando() ? 'Creando…' : 'Crear pedido' }}
        </button>
      </div>
    </section>

    <section class="panel">
      <h2>En curso <span class="cuenta">{{ orders().length }}</span></h2>

      <p class="empty" *ngIf="!cargando() && orders().length === 0">
        Todavía no hay pedidos. Crea el primero con el formulario de arriba.
      </p>
      <p class="empty" *ngIf="cargando()">Cargando pedidos…</p>

      <table *ngIf="orders().length">
        <thead>
          <tr>
            <th>Pedido</th><th>Estado</th><th>Total</th><th>Movimientos posibles</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let o of orders()">
            <td>
              <span class="mono">{{ o.id.slice(0, 8) }}</span>
              <span class="sub">{{ o.customerEmail || '—' }}</span>
            </td>
            <td><span class="status" [attr.data-s]="o.status">{{ label(o.status) }}</span></td>
            <td class="mono">{{ o.total | number }}</td>
            <td>
              <div class="pasos">
                <button *ngFor="let s of todos"
                        type="button"
                        class="paso"
                        [class.bloqueado]="!permite(o.status, s)"
                        [disabled]="s === o.status || terminal(o.status)"
                        (click)="mover(o, s)"
                        [attr.title]="permite(o.status, s)
                          ? 'Movimiento permitido'
                          : 'Bloqueado por la máquina de estados — al pulsarlo el servidor responde 409'">
                  {{ label(s) }}
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  `,
  styles: [`
    .form { display: flex; gap: 1rem; align-items: flex-end; flex-wrap: wrap; }
    .cuenta { font-family: var(--body); font-size: .8rem; font-weight: 500;
      color: var(--muted); margin-left: .4rem; }
    .sub { display: block; font-size: .78rem; color: var(--muted); }
    .pasos { display: flex; gap: .3rem; flex-wrap: wrap; }
    .paso {
      padding: .25rem .55rem; font-size: .78rem; border-radius: 3px;
      border: 1px solid var(--line); background: var(--surface); cursor: pointer;
    }
    .paso:hover:not(:disabled) { border-color: var(--ink); }
    .paso:disabled { opacity: .25; cursor: not-allowed; }
    .paso.bloqueado { text-decoration: line-through; color: var(--clay);
      border-style: dashed; border-color: #e0b6ae; }
  `]
})
export class OrdersComponent implements OnInit {
  private api = inject(ApiService);

  orders = signal<Order[]>([]);
  products = signal<Product[]>([]);
  error = signal('');
  ok = signal('');
  cargando = signal(true);
  creando = signal(false);

  productId = 'P-001';
  quantity = 1;
  customerEmail = '';

  todos: OrderStatus[] = ['CREADO', 'ACEPTADO', 'EN_PREPARACION', 'DESPACHADO', 'ENTREGADO', 'CANCELADO'];

  private grafo: Record<OrderStatus, OrderStatus[]> = {
    CREADO: ['ACEPTADO', 'CANCELADO'],
    ACEPTADO: ['EN_PREPARACION', 'CANCELADO'],
    EN_PREPARACION: ['DESPACHADO', 'CANCELADO'],
    DESPACHADO: ['ENTREGADO'],
    ENTREGADO: [],
    CANCELADO: []
  };

  ngOnInit(): void {
    this.load();
    this.api.listProducts().subscribe({
      next: p => { this.products.set(p); if (p.length) this.productId = p[0].id; },
      error: () => { }
    });
  }

  label(s: OrderStatus): string {
    return s === 'EN_PREPARACION' ? 'En preparación' : s.charAt(0) + s.slice(1).toLowerCase();
  }

  permite(desde: OrderStatus, hacia: OrderStatus): boolean {
    return (this.grafo[desde] ?? []).includes(hacia);
  }

  terminal(s: OrderStatus): boolean {
    return (this.grafo[s] ?? []).length === 0;
  }

  load(): void {
    this.cargando.set(true);
    this.api.listOrders().subscribe({
      next: d => { this.orders.set(d); this.cargando.set(false); },
      error: e => { this.error.set(this.msg(e, 'No se pudieron cargar los pedidos')); this.cargando.set(false); }
    });
  }

  create(): void {
    this.error.set(''); this.ok.set(''); this.creando.set(true);
    const precio = this.products().find(p => p.id === this.productId)?.price ?? 0;
    this.api.createOrder({
      customerEmail: this.customerEmail || undefined,
      items: [{ productId: this.productId, quantity: this.quantity, unitPrice: precio }]
    }).subscribe({
      next: o => {
        this.ok.set(`Pedido ${o.id.slice(0, 8)} creado. Se publicó OrderCreated en Kafka y un comando email.send en RabbitMQ.`);
        this.creando.set(false);
        this.load();
      },
      error: e => { this.error.set(this.msg(e, 'No se pudo crear el pedido')); this.creando.set(false); }
    });
  }

  mover(o: Order, destino: OrderStatus): void {
    this.error.set(''); this.ok.set('');
    this.api.changeStatus(o.id, destino).subscribe({
      next: () => { this.ok.set(`Pedido ${o.id.slice(0, 8)} movido a ${this.label(destino)}.`); this.load(); },
      error: e => this.error.set(this.msg(e, 'El servidor rechazó el movimiento'))
    });
  }

  private msg(e: any, fallback: string): string {
    if (e?.status === 409) return e.error?.message ?? 'Transición inválida (409).';
    if (e?.status === 403) return 'Tu rol no permite esta acción (403).';
    if (e?.status === 401) return 'El token no fue aceptado (401). Revisa la página de Diagnóstico.';
    return `${fallback}: ${e?.error?.message ?? e?.message ?? 'error desconocido'}`;
  }
}
