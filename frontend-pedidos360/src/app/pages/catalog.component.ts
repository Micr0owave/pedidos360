import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, Product } from '../core/api.service';

@Component({
  selector: 'app-catalog',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <h1>Catálogo</h1>
    <p class="hint">El stock baja automáticamente cuando un pedido pasa a Aceptado, no al crearse.</p>

    <div class="alert alert-error" *ngIf="error()">{{ error() }}</div>

    <section class="panel">
      <h2>Agregar o actualizar producto</h2>
      <div class="form">
        <label class="field"><span>Código</span><input [(ngModel)]="nuevo.id" name="id" placeholder="P-005" style="width:7rem"></label>
        <label class="field"><span>Nombre</span><input [(ngModel)]="nuevo.name" name="name" placeholder="Empanada de pino"></label>
        <label class="field"><span>Precio</span><input type="number" [(ngModel)]="nuevo.price" name="price" style="width:7rem"></label>
        <label class="field"><span>Stock</span><input type="number" [(ngModel)]="nuevo.stock" name="stock" style="width:6rem"></label>
        <button type="button" class="btn" (click)="guardar()" [disabled]="!nuevo.id || !nuevo.name">Guardar</button>
      </div>
    </section>

    <section class="panel">
      <h2>Productos</h2>
      <table>
        <thead><tr><th>Código</th><th>Nombre</th><th>Precio</th><th>Stock</th></tr></thead>
        <tbody>
          <tr *ngFor="let p of products()">
            <td class="mono">{{ p.id }}</td>
            <td>{{ p.name }}</td>
            <td class="mono">{{ p.price | number }}</td>
            <td>
              <span class="stock" [class.bajo]="p.stock < 10">{{ p.stock }}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  `,
  styles: [`
    .form { display: flex; gap: 1rem; align-items: flex-end; flex-wrap: wrap; }
    .stock { font-family: ui-monospace, monospace; }
    .stock.bajo { color: var(--clay); font-weight: 600; }
  `]
})
export class CatalogComponent implements OnInit {
  private api = inject(ApiService);
  products = signal<Product[]>([]);
  error = signal('');
  nuevo: Product = { id: '', name: '', price: 0, stock: 0 };

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.listProducts().subscribe({
      next: d => this.products.set(d),
      error: e => this.error.set(`No se pudo cargar el catálogo (HTTP ${e.status}).`)
    });
  }

  guardar(): void {
    this.error.set('');
    this.api.saveProduct(this.nuevo).subscribe({
      next: () => { this.nuevo = { id: '', name: '', price: 0, stock: 0 }; this.load(); },
      error: e => this.error.set(`No se pudo guardar (HTTP ${e.status}).`)
    });
  }
}
