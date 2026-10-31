import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ClienteService } from './cliente.service';
import { ClienteRequest, ClienteResponse } from './cliente.model';
import { environment } from '../../../environments/environment';

const BASE = `${environment.apiUrl}/clientes`;

describe('ClienteService', () => {
  let service: ClienteService;
  let httpTesting: HttpTestingController;

  const cliente: ClienteResponse = {
    id: 1, nome: 'Cliente Teste', cpfCnpj: '123.456.789-00', telefone: '11999999999',
    email: 'cliente@teste.com', status: 'ATIVO', veiculos: [],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(ClienteService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('deve criar o servico', () => {
    expect(service).toBeTruthy();
  });

  it('meuPerfil deve chamar GET em /clientes/me', () => {
    service.meuPerfil().subscribe();

    const req = httpTesting.expectOne(`${BASE}/me`);
    expect(req.request.method).toBe('GET');
    req.flush(cliente);
  });

  it('listarTodos deve chamar GET em /clientes', () => {
    service.listarTodos().subscribe();

    const req = httpTesting.expectOne(BASE);
    expect(req.request.method).toBe('GET');
    req.flush([cliente]);
  });

  it('buscarPorId deve chamar GET em /clientes/{id}', () => {
    service.buscarPorId(1).subscribe();

    const req = httpTesting.expectOne(`${BASE}/1`);
    expect(req.request.method).toBe('GET');
    req.flush(cliente);
  });

  it('buscarPorDocumento deve chamar GET em /clientes/{documento}', () => {
    service.buscarPorDocumento('123.456.789-00').subscribe();

    const req = httpTesting.expectOne(`${BASE}/123.456.789-00`);
    expect(req.request.method).toBe('GET');
    req.flush(cliente);
  });

  it('cadastrar deve chamar POST em /clientes com o body correto', () => {
    const body: ClienteRequest = { nome: 'Cliente Teste', cpfCnpj: '123.456.789-00', telefone: '11999999999', email: 'cliente@teste.com' };

    service.cadastrar(body).subscribe();

    const req = httpTesting.expectOne(BASE);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush(cliente);
  });

  it('atualizar deve chamar PATCH em /clientes/{id}/atualizacao', () => {
    const body: ClienteRequest = { nome: 'Cliente Atualizado', cpfCnpj: '123.456.789-00', telefone: '11999999999', email: 'cliente@teste.com' };

    service.atualizar(1, body).subscribe();

    const req = httpTesting.expectOne(`${BASE}/1/atualizacao`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(body);
    req.flush(cliente);
  });

  it('alterarStatus deve chamar PATCH em /clientes/{id}/status com o status', () => {
    service.alterarStatus(1, 'INATIVO').subscribe();

    const req = httpTesting.expectOne(`${BASE}/1/status`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'INATIVO' });
    req.flush({ ...cliente, status: 'INATIVO' });
  });

  it('deletar deve chamar DELETE em /clientes/{id} esperando resposta texto', () => {
    let resultado: string | undefined;
    service.deletar(1).subscribe((r) => (resultado = r));

    const req = httpTesting.expectOne(`${BASE}/1`);
    expect(req.request.method).toBe('DELETE');
    expect(req.request.responseType).toBe('text');
    req.flush('Cliente removido');

    expect(resultado).toBe('Cliente removido');
  });
});
