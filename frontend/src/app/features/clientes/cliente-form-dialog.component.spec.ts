import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';
import { ClienteFormDialogComponent, ClienteFormDialogData } from './cliente-form-dialog.component';
import { ClienteService } from './cliente.service';
import { ClienteResponse } from './cliente.model';

describe('ClienteFormDialogComponent', () => {
  let mockClienteService: jasmine.SpyObj<ClienteService>;
  let mockDialogRef: jasmine.SpyObj<MatDialogRef<ClienteFormDialogComponent>>;
  let mockSnackBar: jasmine.SpyObj<MatSnackBar>;

  const clienteExistente: ClienteResponse = {
    id: 1, nome: 'Cliente Teste', cpfCnpj: '12345678900', telefone: '11999998888',
    email: 'cliente@teste.com', status: 'ATIVO', veiculos: [],
  };

  function criarComponente(data: ClienteFormDialogData): ClienteFormDialogComponent {
    TestBed.configureTestingModule({
      providers: [
        { provide: ClienteService, useValue: mockClienteService },
        { provide: MatDialogRef, useValue: mockDialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatSnackBar, useValue: mockSnackBar },
      ],
    });

    return TestBed.runInInjectionContext(() => new ClienteFormDialogComponent());
  }

  function preencherFormularioValido(component: ClienteFormDialogComponent): void {
    component.form.setValue({
      nome: 'Novo Cliente',
      cpfCnpj: '529.982.247-25',
      telefone: '(11) 99999-8888',
      email: 'novo@teste.com',
    });
  }

  beforeEach(() => {
    mockClienteService = jasmine.createSpyObj('ClienteService', ['cadastrar', 'atualizar']);
    mockDialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    mockSnackBar = jasmine.createSpyObj('MatSnackBar', ['open']);
  });

  describe('modo cadastro', () => {
    it('deve iniciar em modo cadastro com formulario vazio e cpfCnpj habilitado', () => {
      const component = criarComponente({ cliente: null });

      expect(component.edicao).toBeFalse();
      expect(component.f['cpfCnpj'].disabled).toBeFalse();
      expect(component.form.value.nome).toBe('');
    });

    it('salvar nao deve chamar o servico quando o formulario e invalido', () => {
      const component = criarComponente({ cliente: null });

      component.salvar();

      expect(mockClienteService.cadastrar).not.toHaveBeenCalled();
      expect(component.form.touched).toBeTrue();
    });

    it('salvar deve chamar cadastrar com cpfCnpj apenas com digitos', () => {
      mockClienteService.cadastrar.and.returnValue(of(clienteExistente));
      const component = criarComponente({ cliente: null });
      preencherFormularioValido(component);

      component.salvar();

      expect(mockClienteService.cadastrar).toHaveBeenCalledWith({
        nome: 'Novo Cliente',
        cpfCnpj: '52998224725',
        telefone: '(11) 99999-8888',
        email: 'novo@teste.com',
      });
      expect(mockSnackBar.open).toHaveBeenCalledWith('Cliente cadastrado com sucesso!', 'Fechar', { duration: 3000 });
      expect(mockDialogRef.close).toHaveBeenCalledWith(true);
    });
  });

  describe('modo edicao', () => {
    it('deve preencher o formulario com os dados formatados e desabilitar cpfCnpj', () => {
      const component = criarComponente({ cliente: clienteExistente });

      expect(component.edicao).toBeTrue();
      expect(component.form.value.nome).toBe('Cliente Teste');
      expect(component.form.getRawValue().cpfCnpj).toBe('123.456.789-00');
      expect(component.form.value.telefone).toBe('(11) 99999-8888');
      expect(component.f['cpfCnpj'].disabled).toBeTrue();
    });

    it('salvar deve chamar atualizar com o id do cliente e cpfCnpj apenas com digitos (getRawValue)', () => {
      mockClienteService.atualizar.and.returnValue(of(clienteExistente));
      const component = criarComponente({ cliente: clienteExistente });
      component.form.patchValue({ nome: 'Cliente Atualizado' });

      component.salvar();

      expect(mockClienteService.atualizar).toHaveBeenCalledWith(1, {
        nome: 'Cliente Atualizado',
        cpfCnpj: '12345678900',
        telefone: '(11) 99999-8888',
        email: 'cliente@teste.com',
      });
      expect(mockSnackBar.open).toHaveBeenCalledWith('Cliente atualizado com sucesso!', 'Fechar', { duration: 3000 });
      expect(mockDialogRef.close).toHaveBeenCalledWith(true);
    });
  });

  describe('mascaras', () => {
    it('mascaraCpfCnpj deve formatar o valor do input e do control', () => {
      const component = criarComponente({ cliente: null });
      const input = document.createElement('input');
      input.value = '52998224725';
      const event = { target: input } as unknown as Event;

      component.mascaraCpfCnpj(event);

      expect(input.value).toBe('529.982.247-25');
      expect(component.f['cpfCnpj'].value).toBe('529.982.247-25');
    });

    it('mascaraTelefone deve formatar o valor do input e do control', () => {
      const component = criarComponente({ cliente: null });
      const input = document.createElement('input');
      input.value = '11999998888';
      const event = { target: input } as unknown as Event;

      component.mascaraTelefone(event);

      expect(input.value).toBe('(11) 99999-8888');
      expect(component.f['telefone'].value).toBe('(11) 99999-8888');
    });
  });

  describe('cancelar', () => {
    it('deve fechar o dialog com false', () => {
      const component = criarComponente({ cliente: null });

      component.cancelar();

      expect(mockDialogRef.close).toHaveBeenCalledWith(false);
    });
  });

  describe('tratamento de erro do backend', () => {
    it('deve exibir mensagem padrao quando nao ha corpo no erro', () => {
      mockClienteService.cadastrar.and.returnValue(throwError(() => ({})));
      const component = criarComponente({ cliente: null });
      preencherFormularioValido(component);

      component.salvar();

      expect(component.erroBackend()).toBe('Erro inesperado. Tente novamente.');
      expect(component.loading()).toBeFalse();
    });

    it('deve exibir a mensagem quando o corpo do erro e uma string', () => {
      mockClienteService.cadastrar.and.returnValue(throwError(() => ({ error: 'Falha no servidor' })));
      const component = criarComponente({ cliente: null });
      preencherFormularioValido(component);

      component.salvar();

      expect(component.erroBackend()).toBe('Falha no servidor');
    });

    it('deve exibir a mensagem de erro de negocio quando o corpo possui "erro"', () => {
      mockClienteService.cadastrar.and.returnValue(throwError(() => ({ error: { erro: 'CPF ja cadastrado' } })));
      const component = criarComponente({ cliente: null });
      preencherFormularioValido(component);

      component.salvar();

      expect(component.erroBackend()).toBe('CPF ja cadastrado');
    });

    it('deve mapear erros de validacao por campo do backend', () => {
      mockClienteService.cadastrar.and.returnValue(throwError(() => ({ error: { email: 'E-mail invalido' } })));
      const component = criarComponente({ cliente: null });
      preencherFormularioValido(component);

      component.salvar();

      expect(component.form.get('email')?.errors).toEqual({ backend: 'E-mail invalido' });
      expect(component.erroBackend()).toBeNull();
    });

    it('deve exibir mensagem padrao quando o corpo nao mapeia nenhum campo conhecido', () => {
      mockClienteService.cadastrar.and.returnValue(throwError(() => ({ error: { campoDesconhecido: 'x' } })));
      const component = criarComponente({ cliente: null });
      preencherFormularioValido(component);

      component.salvar();

      expect(component.erroBackend()).toBe('Erro ao processar a requisição.');
    });
  });
});
