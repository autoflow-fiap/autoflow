import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';
import { VeiculoFormDialogComponent, VeiculoFormDialogData } from './veiculo-form-dialog.component';
import { VeiculoService } from './veiculo.service';
import { ClienteService } from '../clientes/cliente.service';
import { AuthService } from '../../core/services/auth.service';
import { VeiculoResponse } from './veiculo.model';
import { ClienteResponse } from '../clientes/cliente.model';

describe('VeiculoFormDialogComponent', () => {
  let mockVeiculoService: jasmine.SpyObj<VeiculoService>;
  let mockClienteService: jasmine.SpyObj<ClienteService>;
  let mockAuth: jasmine.SpyObj<AuthService>;
  let mockDialogRef: jasmine.SpyObj<MatDialogRef<VeiculoFormDialogComponent>>;
  let mockSnackBar: jasmine.SpyObj<MatSnackBar>;

  const clienteResumo = { id: 1, nome: 'Cliente Teste', cpfCnpj: '123.456.789-00', telefone: '11999999999', email: 'cliente@teste.com' };
  const veiculoExistente: VeiculoResponse = { id: 1, marca: 'Fiat', ano: 2020, placa: 'ABC1D23', modelo: 'Uno', cliente: clienteResumo };
  const perfilCliente: ClienteResponse = { id: 1, nome: 'Cliente Teste', cpfCnpj: '12345678900', telefone: '11999999999', email: 'cliente@teste.com', status: 'ATIVO', veiculos: [] };

  function criarComponente(data: VeiculoFormDialogData, role: string | null = 'ADMIN'): VeiculoFormDialogComponent {
    mockAuth.getRole.and.returnValue(role);
    TestBed.configureTestingModule({
      providers: [
        { provide: VeiculoService, useValue: mockVeiculoService },
        { provide: ClienteService, useValue: mockClienteService },
        { provide: AuthService, useValue: mockAuth },
        { provide: MatDialogRef, useValue: mockDialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatSnackBar, useValue: mockSnackBar },
      ],
    });

    return TestBed.runInInjectionContext(() => new VeiculoFormDialogComponent());
  }

  function preencherFormularioValido(component: VeiculoFormDialogComponent): void {
    component.form.patchValue({
      cpfCnpj: '12345678900',
      marca: 'Fiat',
      modelo: 'Uno',
      ano: 2021,
      placa: 'ABC1D23',
    });
  }

  beforeEach(() => {
    mockVeiculoService = jasmine.createSpyObj('VeiculoService', ['cadastrar', 'atualizar']);
    mockClienteService = jasmine.createSpyObj('ClienteService', ['meuPerfil']);
    mockAuth = jasmine.createSpyObj('AuthService', ['getRole']);
    mockDialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    mockSnackBar = jasmine.createSpyObj('MatSnackBar', ['open']);
  });

  describe('modo cadastro - usuario ADMIN/ATENDENTE', () => {
    it('deve iniciar com cpfCnpj obrigatorio e habilitado, sem buscar perfil', () => {
      const component = criarComponente({ veiculo: null }, 'ADMIN');

      expect(component.edicao).toBeFalse();
      expect(component.isCliente).toBeFalse();
      expect(component.f['cpfCnpj'].disabled).toBeFalse();
      expect(component.f['cpfCnpj'].hasError('required')).toBeTrue();
      expect(mockClienteService.meuPerfil).not.toHaveBeenCalled();
    });

    it('salvar nao deve chamar o servico quando o formulario e invalido', () => {
      const component = criarComponente({ veiculo: null }, 'ADMIN');

      component.salvar();

      expect(mockVeiculoService.cadastrar).not.toHaveBeenCalled();
      expect(component.form.touched).toBeTrue();
    });

    it('salvar deve chamar cadastrar com cpfCnpj apenas digitos e placa normalizada', () => {
      mockVeiculoService.cadastrar.and.returnValue(of(veiculoExistente));
      const component = criarComponente({ veiculo: null }, 'ADMIN');
      preencherFormularioValido(component);
      component.form.patchValue({ placa: 'abc-1d23' });

      component.salvar();

      expect(mockVeiculoService.cadastrar).toHaveBeenCalledWith({
        cpfCnpj: '12345678900',
        marca: 'Fiat',
        modelo: 'Uno',
        ano: 2021,
        placa: 'ABC1D23',
      });
      expect(mockSnackBar.open).toHaveBeenCalledWith('Veículo cadastrado com sucesso!', 'Fechar', { duration: 3000 });
      expect(mockDialogRef.close).toHaveBeenCalledWith(true);
    });
  });

  describe('modo cadastro - usuario CLIENTE', () => {
    it('deve carregar o perfil do cliente e preencher/desabilitar cpfCnpj automaticamente', () => {
      mockClienteService.meuPerfil.and.returnValue(of(perfilCliente));
      const component = criarComponente({ veiculo: null }, 'CLIENTE');

      expect(component.isCliente).toBeTrue();
      expect(mockClienteService.meuPerfil).toHaveBeenCalled();
      expect(component.f['cpfCnpj'].value).toBe('12345678900');
      expect(component.f['cpfCnpj'].disabled).toBeTrue();
      expect(component.carregandoPerfil()).toBeFalse();
    });

    it('deve exibir mensagem de erro quando falha ao carregar o perfil', () => {
      mockClienteService.meuPerfil.and.returnValue(throwError(() => ({})));
      const component = criarComponente({ veiculo: null }, 'CLIENTE');

      expect(component.erroBackend()).toBe('Não foi possível carregar seu perfil. Informe o CPF/CNPJ manualmente.');
      expect(component.carregandoPerfil()).toBeFalse();
    });
  });

  describe('modo edicao', () => {
    it('deve preencher o formulario com os dados do veiculo e desabilitar cpfCnpj', () => {
      const component = criarComponente({ veiculo: veiculoExistente }, 'ADMIN');

      expect(component.edicao).toBeTrue();
      expect(component.form.value.marca).toBe('Fiat');
      expect(component.form.value.modelo).toBe('Uno');
      expect(component.form.value.ano).toBe(2020);
      expect(component.form.value.placa).toBe('ABC1D23');
      expect(component.f['cpfCnpj'].disabled).toBeTrue();
      expect(mockClienteService.meuPerfil).not.toHaveBeenCalled();
    });

    it('salvar deve chamar atualizar com o id do veiculo, sem cpfCnpj no payload', () => {
      mockVeiculoService.atualizar.and.returnValue(of(veiculoExistente));
      const component = criarComponente({ veiculo: veiculoExistente }, 'ADMIN');
      component.form.patchValue({ marca: 'Fiat Atualizado' });

      component.salvar();

      expect(mockVeiculoService.atualizar).toHaveBeenCalledWith(1, {
        marca: 'Fiat Atualizado',
        modelo: 'Uno',
        ano: 2020,
        placa: 'ABC1D23',
      });
      expect(mockSnackBar.open).toHaveBeenCalledWith('Veículo atualizado com sucesso!', 'Fechar', { duration: 3000 });
      expect(mockDialogRef.close).toHaveBeenCalledWith(true);
    });
  });

  describe('normalizarPlacaInput', () => {
    it('deve normalizar o valor do input e do control de placa', () => {
      const component = criarComponente({ veiculo: null }, 'ADMIN');
      const input = document.createElement('input');
      input.value = 'abc-1d23';
      const event = { target: input } as unknown as Event;

      component.normalizarPlacaInput(event);

      expect(input.value).toBe('ABC1D23');
      expect(component.f['placa'].value).toBe('ABC1D23');
    });
  });

  describe('cancelar', () => {
    it('deve fechar o dialog com false', () => {
      const component = criarComponente({ veiculo: null }, 'ADMIN');

      component.cancelar();

      expect(mockDialogRef.close).toHaveBeenCalledWith(false);
    });
  });

  describe('tratamento de erro do backend', () => {
    it('deve exibir mensagem padrao quando nao ha corpo no erro', () => {
      mockVeiculoService.cadastrar.and.returnValue(throwError(() => ({})));
      const component = criarComponente({ veiculo: null }, 'ADMIN');
      preencherFormularioValido(component);

      component.salvar();

      expect(component.erroBackend()).toBe('Erro inesperado. Tente novamente.');
      expect(component.loading()).toBeFalse();
    });

    it('deve exibir a mensagem quando o corpo do erro e uma string', () => {
      mockVeiculoService.cadastrar.and.returnValue(throwError(() => ({ error: 'Falha no servidor' })));
      const component = criarComponente({ veiculo: null }, 'ADMIN');
      preencherFormularioValido(component);

      component.salvar();

      expect(component.erroBackend()).toBe('Falha no servidor');
    });

    it('deve exibir a mensagem de erro de negocio quando o corpo possui "erro"', () => {
      mockVeiculoService.cadastrar.and.returnValue(throwError(() => ({ error: { erro: 'Placa ja cadastrada' } })));
      const component = criarComponente({ veiculo: null }, 'ADMIN');
      preencherFormularioValido(component);

      component.salvar();

      expect(component.erroBackend()).toBe('Placa ja cadastrada');
    });

    it('deve mapear erros de validacao por campo do backend', () => {
      mockVeiculoService.cadastrar.and.returnValue(throwError(() => ({ error: { placa: 'Placa invalida' } })));
      const component = criarComponente({ veiculo: null }, 'ADMIN');
      preencherFormularioValido(component);

      component.salvar();

      expect(component.form.get('placa')?.errors).toEqual({ backend: 'Placa invalida' });
      expect(component.erroBackend()).toBeNull();
    });

    it('deve exibir mensagem padrao quando o corpo nao mapeia nenhum campo conhecido', () => {
      mockVeiculoService.cadastrar.and.returnValue(throwError(() => ({ error: { campoDesconhecido: 'x' } })));
      const component = criarComponente({ veiculo: null }, 'ADMIN');
      preencherFormularioValido(component);

      component.salvar();

      expect(component.erroBackend()).toBe('Erro ao processar a requisição.');
    });
  });
});
