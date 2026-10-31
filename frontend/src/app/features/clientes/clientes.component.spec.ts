import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { ClienteService } from './cliente.service';
import { ClientesComponent } from './clientes.component';
import { ClienteResponse } from './cliente.model';

describe('ClientesComponent', () => {
  let mockClienteService: jasmine.SpyObj<ClienteService>;
  let mockDialog: jasmine.SpyObj<MatDialog>;
  let mockSnackBar: jasmine.SpyObj<MatSnackBar>;
  let mockAuth: jasmine.SpyObj<AuthService>;

  const cliente: ClienteResponse = {
    id: 1,
    nome: 'Cliente Teste',
    cpfCnpj: '12345678900',
    telefone: '11999999999',
    email: 'cliente@teste.com',
    status: 'ATIVO',
    veiculos: [],
  };

  beforeEach(() => {
    mockClienteService = jasmine.createSpyObj('ClienteService', [
      'listarTodos', 'buscarPorDocumento', 'alterarStatus', 'deletar',
    ]);
    mockDialog = jasmine.createSpyObj('MatDialog', ['open']);
    mockSnackBar = jasmine.createSpyObj('MatSnackBar', ['open']);
    mockAuth = jasmine.createSpyObj('AuthService', ['getRole']);

    TestBed.configureTestingModule({
      providers: [
        { provide: ClienteService, useValue: mockClienteService },
        { provide: MatDialog, useValue: mockDialog },
        { provide: MatSnackBar, useValue: mockSnackBar },
        { provide: AuthService, useValue: mockAuth },
      ],
    });
  });

  function criarComponente(role: string | null = 'ADMIN'): ClientesComponent {
    mockAuth.getRole.and.returnValue(role);
    return TestBed.runInInjectionContext(() => new ClientesComponent());
  }

  it('deve exibir o status com o rotulo correspondente', () => {
    const component = criarComponente();

    expect(component.labelStatus('ATIVO')).toBe('Ativo');
    expect(component.labelStatus('INATIVO')).toBe('Inativo');
  });

  it('deve permitir acoes de status apenas para ADMIN', () => {
    expect(criarComponente('ADMIN').isAdmin).toBeTrue();
    expect(criarComponente('ATENDENTE').isAdmin).toBeFalse();
  });

  it('deve alterar status depois da confirmacao e atualizar a linha', () => {
    const atualizado = { ...cliente, status: 'INATIVO' as const };
    mockDialog.open.and.returnValue({ afterClosed: () => of(true) } as any);
    mockClienteService.alterarStatus.and.returnValue(of(atualizado));
    const component = criarComponente();
    component.clientes.set([cliente]);

    component.confirmarAlteracaoStatus(cliente);

    expect(mockClienteService.alterarStatus).toHaveBeenCalledWith(1, 'INATIVO');
    expect(component.clientes()[0].status).toBe('INATIVO');
    expect(mockSnackBar.open).toHaveBeenCalledWith(
      'Cliente desativado com sucesso.', 'Fechar', { duration: 3000 });
  });

  it('nao deve alterar status quando a confirmacao e cancelada', () => {
    mockDialog.open.and.returnValue({ afterClosed: () => of(false) } as any);
    const component = criarComponente();

    component.confirmarAlteracaoStatus(cliente);

    expect(mockClienteService.alterarStatus).not.toHaveBeenCalled();
  });

  it('deve informar erro e manter status quando a alteracao falha', () => {
    mockDialog.open.and.returnValue({ afterClosed: () => of(true) } as any);
    mockClienteService.alterarStatus.and.returnValue(
      throwError(() => ({ error: { erro: 'Cliente bloqueado' } })));
    const component = criarComponente();
    component.clientes.set([cliente]);

    component.confirmarAlteracaoStatus(cliente);

    expect(component.clientes()[0].status).toBe('ATIVO');
    expect(mockSnackBar.open).toHaveBeenCalledWith(
      'Cliente bloqueado', 'Fechar', { duration: 4000 });
  });
});
