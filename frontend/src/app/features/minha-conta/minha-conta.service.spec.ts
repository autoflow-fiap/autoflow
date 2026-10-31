import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MinhaContaService } from './minha-conta.service';
import { AcompanhamentoOrdemServicoResponse, ClienteLogadoResponse } from './minha-conta.model';
import { environment } from '../../../environments/environment';

const BASE = `${environment.apiUrl}/clientes/me`;

describe('MinhaContaService', () => {
  let service: MinhaContaService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(MinhaContaService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('deve criar o servico', () => {
    expect(service).toBeTruthy();
  });

  it('buscarPerfil deve chamar GET em /clientes/me', () => {
    const perfil: ClienteLogadoResponse = {
      id: 1,
      nome: 'Cliente Teste',
      cpfCnpj: '123.456.789-00',
      telefone: '11999999999',
      email: 'cliente@teste.com',
      status: 'ATIVO',
      veiculos: [],
    };

    let resultado: ClienteLogadoResponse | undefined;
    service.buscarPerfil().subscribe((r) => (resultado = r));

    const req = httpTesting.expectOne(BASE);
    expect(req.request.method).toBe('GET');
    req.flush(perfil);

    expect(resultado).toEqual(perfil);
  });

  it('listarMinhasOrdens deve chamar GET em /clientes/me/ordens-servico', () => {
    let resultado: AcompanhamentoOrdemServicoResponse[] | undefined;
    service.listarMinhasOrdens().subscribe((r) => (resultado = r));

    const req = httpTesting.expectOne(`${BASE}/ordens-servico`);
    expect(req.request.method).toBe('GET');
    req.flush([]);

    expect(resultado).toEqual([]);
  });

  it('buscarMinhaOrdem deve retornar a ordem cujo numeroOs corresponde', () => {
    const ordens: AcompanhamentoOrdemServicoResponse[] = [
      criarOrdem('OS-001'),
      criarOrdem('OS-002'),
    ];

    let resultado: AcompanhamentoOrdemServicoResponse | null | undefined;
    service.buscarMinhaOrdem('OS-002').subscribe((r) => (resultado = r));

    const req = httpTesting.expectOne(`${BASE}/ordens-servico`);
    req.flush(ordens);

    expect(resultado?.numeroOs).toBe('OS-002');
  });

  it('buscarMinhaOrdem deve retornar null quando nao encontra numeroOs correspondente', () => {
    const ordens: AcompanhamentoOrdemServicoResponse[] = [criarOrdem('OS-001')];

    let resultado: AcompanhamentoOrdemServicoResponse | null | undefined;
    service.buscarMinhaOrdem('OS-999').subscribe((r) => (resultado = r));

    const req = httpTesting.expectOne(`${BASE}/ordens-servico`);
    req.flush(ordens);

    expect(resultado).toBeNull();
  });

  function criarOrdem(numeroOs: string): AcompanhamentoOrdemServicoResponse {
    return {
      numeroOs,
      placa: 'ABC1D23',
      statusAtual: 'RECEBIDA',
      dataAbertura: '2026-01-01T10:00:00Z',
      ultimaAtualizacao: '2026-01-01T10:00:00Z',
      servicosSolicitados: [],
      orcamentoAtual: null,
      situacaoAprovacao: null,
      mensagemParaCliente: '',
      historicoStatus: [],
    };
  }
});
