import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../core/services/auth.service';
import { ClienteService } from './cliente.service';
import {
  ClienteResponse,
  ClienteStatus,
  formatarCpfCnpj,
  formatarTelefone,
} from './cliente.model';
import { ClienteFormDialogComponent } from './cliente-form-dialog.component';
import {
  ConfirmacaoDialogComponent,
  ConfirmacaoDialogData,
} from '../../shared/dialogs/confirmacao-dialog.component';

@Component({
  selector: 'app-clientes',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatCardModule,
    MatChipsModule,
    MatDividerModule,
  ],
  templateUrl: './clientes.component.html',
  styleUrl: './clientes.component.scss',
})
export class ClientesComponent implements OnInit {
  private readonly clienteService = inject(ClienteService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly auth = inject(AuthService);

  readonly clientes = signal<ClienteResponse[]>([]);
  readonly loading = signal(true);
  readonly erroCarregamento = signal<string | null>(null);
  readonly isAdmin = this.auth.getRole() === 'ADMIN';

  expandido: ClienteResponse | null = null;

  filtroDocumento = '';

  readonly colunas = ['expandir', 'nome', 'cpfCnpj', 'telefone', 'email', 'status', 'acoes'];
  readonly fmt = formatarCpfCnpj;
  readonly fmtTel = formatarTelefone;

  get temFiltroDocumento(): boolean {
    return !!this.filtroDocumento.trim();
  }

  ngOnInit(): void {
    this.carregar();
  }

  carregar(documento?: string): void {
    this.expandido = null;
    this.loading.set(true);
    this.erroCarregamento.set(null);

    const filtro = (documento ?? this.filtroDocumento).trim();
    const documentoSanitizado = filtro.replace(/\D/g, '');

    if (documentoSanitizado) {
      this.clienteService.buscarPorDocumento(documentoSanitizado).subscribe({
        next: (cliente) => {
          this.clientes.set([cliente]);
          this.loading.set(false);
        },
        error: (err) => {
          const mensagem = err?.status === 404
            ? 'Cliente não encontrado.'
            : 'Não foi possível carregar o cliente.';
          this.erroCarregamento.set(mensagem);
          this.clientes.set([]);
          this.loading.set(false);
        },
      });
      return;
    }

    this.clienteService.listarTodos().subscribe({
      next: (data) => {
        this.clientes.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.erroCarregamento.set('Não foi possível carregar os clientes.');
        this.loading.set(false);
      },
    });
  }

  buscar(): void {
    this.carregar();
  }

  limparFiltros(): void {
    this.filtroDocumento = '';
    this.carregar();
  }

  labelStatus(status: ClienteStatus): string {
    return status === 'ATIVO' ? 'Ativo' : 'Inativo';
  }

  confirmarAlteracaoStatus(cliente: ClienteResponse): void {
    const ativar = cliente.status === 'INATIVO';
    const ref = this.dialog.open<
      ConfirmacaoDialogComponent,
      ConfirmacaoDialogData,
      boolean
    >(ConfirmacaoDialogComponent, {
      width: '400px',
      data: {
        titulo: ativar ? 'Ativar cliente' : 'Desativar cliente',
        mensagem: ativar
          ? `Tem certeza que deseja ativar "${cliente.nome}"?`
          : `Tem certeza que deseja desativar "${cliente.nome}"?`,
        labelConfirmar: ativar ? 'Ativar' : 'Desativar',
      },
    });

    ref.afterClosed().subscribe((confirmado) => {
      if (!confirmado) return;

      const status: ClienteStatus = ativar ? 'ATIVO' : 'INATIVO';
      this.clienteService.alterarStatus(cliente.id, status).subscribe({
        next: (atualizado) => {
          this.clientes.update(clientes => clientes.map(item =>
            item.id === atualizado.id ? atualizado : item));
          this.snackBar.open(
            ativar ? 'Cliente ativado com sucesso.' : 'Cliente desativado com sucesso.',
            'Fechar',
            { duration: 3000 },
          );
        },
        error: (err) => {
          const msg = err?.error?.erro ?? 'Erro ao alterar o status do cliente.';
          this.snackBar.open(msg, 'Fechar', { duration: 4000 });
        },
      });
    });
  }

  toggleExpand(cliente: ClienteResponse, event: Event): void {
    event.stopPropagation();
    this.expandido = this.expandido === cliente ? null : cliente;
  }

  abrirFormulario(cliente?: ClienteResponse): void {
    const ref = this.dialog.open(ClienteFormDialogComponent, {
      width: '540px',
      disableClose: true,
      data: { cliente: cliente ?? null },
    });
    ref.afterClosed().subscribe((salvo: boolean) => {
      if (salvo) this.carregar();
    });
  }

  confirmarExclusao(cliente: ClienteResponse): void {
    const ref = this.dialog.open<
      ConfirmacaoDialogComponent,
      ConfirmacaoDialogData,
      boolean
    >(ConfirmacaoDialogComponent, {
      width: '400px',
      data: {
        titulo: 'Excluir cliente',
        mensagem: `Tem certeza que deseja excluir "${cliente.nome}"? Esta ação não pode ser desfeita.`,
        labelConfirmar: 'Excluir',
      },
    });

    ref.afterClosed().subscribe((confirmado) => {
      if (!confirmado) return;
      this.clienteService.deletar(cliente.id).subscribe({
        next: () => {
          this.snackBar.open('Cliente excluído com sucesso.', 'Fechar', { duration: 3000 });
          this.carregar();
        },
        error: (err) => {
          const msg = err?.error?.erro ?? 'Erro ao excluir o cliente.';
          this.snackBar.open(msg, 'Fechar', { duration: 4000 });
        },
      });
    });
  }
}
