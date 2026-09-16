import { Routes } from '@angular/router';
import { MsalGuard } from '@azure/msal-angular';
import { roleGuard } from './core/role.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },

  { path: 'login', loadComponent: () => import('./pages/login.component').then(m => m.LoginComponent) },
  { path: 'auth/callback', redirectTo: 'dashboard', pathMatch: 'full' },

  {
    path: 'dashboard',
    canActivate: [MsalGuard],
    loadComponent: () => import('./pages/dashboard.component').then(m => m.DashboardComponent)
  },
  {
    path: 'orders',
    canActivate: [MsalGuard],
    loadComponent: () => import('./pages/orders.component').then(m => m.OrdersComponent)
  },
  {
    path: 'catalog',
    canActivate: [MsalGuard, roleGuard(['Admin', 'Operador'])],
    loadComponent: () => import('./pages/catalog.component').then(m => m.CatalogComponent)
  },
  {
    path: 'reports',
    canActivate: [MsalGuard, roleGuard(['Admin'])],
    loadComponent: () => import('./pages/reports.component').then(m => m.ReportsComponent)
  },
  {
    path: 'audit',
    canActivate: [MsalGuard, roleGuard(['Admin', 'Auditor'])],
    loadComponent: () => import('./pages/audit.component').then(m => m.AuditComponent)
  },
  {
    path: 'diagnostico',
    canActivate: [MsalGuard],
    loadComponent: () => import('./pages/diagnostico.component').then(m => m.DiagnosticoComponent)
  },

  { path: '**', redirectTo: 'dashboard' }
];
