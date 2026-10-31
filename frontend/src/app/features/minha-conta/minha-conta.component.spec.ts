import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';
import { MinhaContaComponent } from './minha-conta.component';
import { MinhaContaService } from './minha-conta.service';
import { ClienteLogadoResponse } from './minha-conta.model';

describe('MinhaContaComponent', () => {
  let mockService: jasmine.SpyObj<MinhaContaService>;
  let mockSnackBar: jasmine.SpyObj<MatSnackBar>;

  const cliente: ClienteLogadoResponse = {
    id: 1, nome: 'Cliente Teste', cpfCnpj: '123.456.789-00', telefone: '11999999999',
    email: 'cliente@teste.com', status: 'ATIVO', veiculos: [],
  };

  beforeEach(() => {
    mockService = jasmine.createSpyObj('MinhaContaService', ['buscarPerfil']);
    mockSnackBar = jasmine.createSpyObj('MatSnackBar', ['open']);

    TestBed.configureTestingModule({
      providers: [
        { provide: MinhaContaService, useValue: mockService },
        { provide: MatSnackBar, useValue: mockSnackBar },
      ],
    });
  });

  function criarComponente(): MinhaContaComponent {
    return TestBed.runInInjectionContext(() => new MinhaContaComponent());
  }

  it('deve carregar o perfil do cliente ao inicializar', () => {
    mockService.buscarPerfil.and.returnValue(of(cliente));
    const component = criarComponente();

    component.ngOnInit();

    expect(component.cliente()).toEqual(cliente);
    expect(component.loading()).toBeFalse();
  });

  it('deve exibir snackbar com mensagem do backend quando falha ao carregar', () => {
    mockService.buscarPerfil.and.returnValue(throwError(() => ({ error: { erro: 'Falha ao buscar perfil' } })));
    const component = criarComponente();

    component.ngOnInit();

    expect(mockSnackBar.open).toHaveBeenCalledWith('Falha ao buscar perfil', 'Fechar', { duration: 5000 });
    expect(component.loading()).toBeFalse();
  });

  it('deve exibir mensagem padrao quando erro nao possui mensagem do backend', () => {
    mockService.buscarPerfil.and.returnValue(throwError(() => ({})));
    const component = criarComponente();

    component.ngOnInit();

    expect(mockSnackBar.open).toHaveBeenCalledWith('Nao foi possivel carregar seus dados.', 'Fechar', { duration: 5000 });
  });
});
