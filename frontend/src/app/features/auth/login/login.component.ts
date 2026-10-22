import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/services/auth.service';
import { cpfCnpjValidator } from '../../clientes/cliente.model';

const ROLE_HOME: Record<string, string> = {
  CLIENTE: '/minha-conta/minhas-ordens',
  ADMIN: '/dashboard',
  ATENDENTE: '/dashboard',
  MECANICO: '/dashboard',
};

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  loading = signal(false);
  errorMsg = signal('');
  hidePassword = signal(true);

  form = this.fb.group({
    cpfCnpj: ['', [Validators.required, cpfCnpjValidator()]],
    senha: ['', [Validators.required]]
  });

  submit(): void {
    const { cpfCnpj, senha } = this.form.value;
    if (this.form.invalid || !cpfCnpj?.trim()) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMsg.set('');

    this.auth.login(cpfCnpj!, senha!).subscribe({
      next: () => {
        const role = this.auth.getRole() ?? '';
        const dest = ROLE_HOME[role] ?? '/dashboard';
        this.router.navigate([dest]);
      },
      error: () => {
        this.errorMsg.set('CPF/CNPJ ou senha inválidos. Verifique suas credenciais.');
        this.loading.set(false);
      }
    });
  }
}
