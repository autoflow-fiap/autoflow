import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from './auth.service';

function criarTokenJwt(payload: object): string {
  const encoded = btoa(JSON.stringify(payload))
    .replaceAll('+', '-')
    .replaceAll('/', '_')
    .replace(/=+$/, '');
  return `header.${encoded}.signature`;
}

describe('AuthService', () => {
  let service: AuthService;
  let httpTesting: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([])
      ]
    });

    service = TestBed.inject(AuthService);
    httpTesting = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpTesting.verify();
    localStorage.clear();
  });

  it('deve criar o servico', () => {
    expect(service).toBeTruthy();
  });

  it('getToken deve retornar null quando nao ha token armazenado', () => {
    expect(service.getToken()).toBeNull();
  });

  it('isLoggedIn deve retornar false quando nao ha sessao ativa', () => {
    expect(service.isLoggedIn()).toBeFalse();
  });

  it('getRole deve retornar null quando nao ha sessao ativa', () => {
    expect(service.getRole()).toBeNull();
  });

  it('getUsuarioLogado deve retornar null quando sem token', () => {
    expect(service.getUsuarioLogado()).toBeNull();
  });

  it('login deve chamar o endpoint same-origin do API Gateway com CPF/CNPJ e armazenar o token', () => {
    const token = criarTokenJwt({ sub: '52998224725', role: 'ADMIN', iat: 0, exp: 9999999999 });

    service.login('52998224725', 'senha123').subscribe();

    const req = httpTesting.expectOne('/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ cpf_cnpj: '52998224725', senha: 'senha123' });
    req.flush({ token });

    expect(service.getToken()).toBe(token);
  });

  it('login deve tornar isLoggedIn verdadeiro apos receber token valido', () => {
    const token = criarTokenJwt({ sub: '12345678909', role: 'MECANICO', iat: 0, exp: 9999999999 });

    service.login('12345678909', 'senha').subscribe();
    httpTesting.expectOne('/auth/login').flush({ token });

    expect(service.isLoggedIn()).toBeTrue();
  });

  it('loginPorCpf deve chamar a API serverless com CPF/CNPJ e armazenar o token', () => {
    const token = criarTokenJwt({ sub: '52998224725', role: 'CLIENTE', iat: 0, exp: 9999999999 });

    service.loginPorCpf('529.982.247-25', 'senha').subscribe();

    const req = httpTesting.expectOne('/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ cpf_cnpj: '529.982.247-25', senha: 'senha' });
    req.flush({ token, tokenType: 'Bearer', expiresIn: 3600 });

    expect(service.getToken()).toBe(token);
  });

  it('getUsuarioLogado deve decodificar CPF/CNPJ e role do token valido', () => {
    const token = criarTokenJwt({ sub: '16899535009', role: 'ATENDENTE', iat: 0, exp: 9999999999 });

    service.login('16899535009', 'senha').subscribe();
    httpTesting.expectOne('/auth/login').flush({ token });

    const usuario = service.getUsuarioLogado();
    expect(usuario?.email).toBe('16899535009');
    expect(usuario?.role).toBe('ATENDENTE');
  });

  it('getRole deve retornar o role do token quando logado', () => {
    const token = criarTokenJwt({ sub: '52998224725', role: 'ADMIN', iat: 0, exp: 9999999999 });

    service.login('52998224725', 'senha').subscribe();
    httpTesting.expectOne('/auth/login').flush({ token });

    expect(service.getRole()).toBe('ADMIN');
  });

  it('logout deve remover o token e navegar para /login', () => {
    const token = criarTokenJwt({ sub: '52998224725', role: 'CLIENTE', iat: 0, exp: 9999999999 });
    const navigateSpy = spyOn(router, 'navigate');

    service.login('52998224725', 'senha').subscribe();
    httpTesting.expectOne('/auth/login').flush({ token });

    service.logout();

    expect(service.getToken()).toBeNull();
    expect(service.isLoggedIn()).toBeFalse();
    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
  });

  it('getUsuarioLogado deve retornar null e chamar logout quando token expirado', () => {
    const tokenExpirado = criarTokenJwt({ sub: '52998224725', role: 'ADMIN', iat: 0, exp: 1 });
    const navigateSpy = spyOn(router, 'navigate');

    service.login('52998224725', 'senha').subscribe();
    httpTesting.expectOne('/auth/login').flush({ token: tokenExpirado });

    const usuario = service.getUsuarioLogado();
    expect(usuario).toBeNull();
    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
  });
});
