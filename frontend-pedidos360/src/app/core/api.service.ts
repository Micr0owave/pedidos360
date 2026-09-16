import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export type OrderStatus = 'CREADO' | 'ACEPTADO' | 'EN_PREPARACION' | 'DESPACHADO' | 'ENTREGADO' | 'CANCELADO';

export interface OrderItem { productId: string; quantity: number; unitPrice: number; }
export interface Order {
  id: string; customerId: string; customerEmail: string;
  status: OrderStatus; total: number; createdAt: string;
  deliveredAt?: string; items: OrderItem[];
}
export interface Product { id: string; name: string; price: number; stock: number; }
export interface MeResponse {
  authenticated: boolean;
  name?: string;
  email?: string;
  roles?: string[];
  authorities?: string[];
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  me(): Observable<MeResponse> { return this.http.get<MeResponse>(`${this.base}/api/me`); }
  ping(): Observable<string> { return this.http.get(`${this.base}/api/ping`, { responseType: 'text' }); }

  // Pedidos
  listOrders(): Observable<Order[]> { return this.http.get<Order[]>(`${this.base}/api/orders`); }
  getOrder(id: string): Observable<Order> { return this.http.get<Order>(`${this.base}/api/orders/${id}`); }
  createOrder(body: { customerEmail?: string; items: OrderItem[] }): Observable<Order> {
    return this.http.post<Order>(`${this.base}/api/orders`, body);
  }
  changeStatus(id: string, target: OrderStatus, reason = ''): Observable<Order> {
    return this.http.patch<Order>(`${this.base}/api/orders/${id}/status`, { target, reason });
  }
  transitions(id: string): Observable<{ current: OrderStatus; allowed: OrderStatus[] }> {
    return this.http.get<{ current: OrderStatus; allowed: OrderStatus[] }>(`${this.base}/api/orders/${id}/transitions`);
  }

  // Catalogo
  listProducts(): Observable<Product[]> { return this.http.get<Product[]>(`${this.base}/api/catalog/products`); }
  saveProduct(p: Product): Observable<Product> { return this.http.post<Product>(`${this.base}/api/catalog/products`, p); }

  // Reporteria
  salesByHour(): Observable<any[]> { return this.http.get<any[]>(`${this.base}/api/report/kpi/sales-by-hour`); }
  leadTime(): Observable<any> { return this.http.get<any>(`${this.base}/api/report/kpi/lead-time`); }
  activeStates(): Observable<Record<string, number>> {
    return this.http.get<Record<string, number>>(`${this.base}/api/report/kpi/active-states`);
  }
  topProducts(): Observable<any[]> { return this.http.get<any[]>(`${this.base}/api/report/kpi/top-products`); }

  // Auditoria
  timeline(orderId: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/api/audit/timeline/${orderId}`);
  }
  auditEvents(params: Record<string, string> = {}): Observable<any[]> {
    const qs = new URLSearchParams(params).toString();
    return this.http.get<any[]>(`${this.base}/api/audit/events${qs ? '?' + qs : ''}`);
  }
}
