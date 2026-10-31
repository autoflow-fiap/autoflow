import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { LoginComponent } from './login.component';
import { AuthService } from '../../../core/services/auth.service';

describe('LoginComponent', () => {
  let mockAuth: jasmine.SpyObj<AuthService>;
  let mockRouter: jasmine.SpyObj<Router>;

  beforeEach(() => {
    mockAuth = jasmine.createSpyObj('AuthService', ['login', 'getRole']);
    mockRouter = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: mockAuth },
        { provide: Router, useValue: mockRouter },
      ],
    });
  });

  function criarComponente(): LoginComponent {
    return TestBed.runInInjectionContext(() => new LoginComponent());
  }

  it('submit nao deve chamar login quando o formulario e invalido', () => {
    const component = criarComponente();

    component.submit();

    expect(mockAuth.login).not.toHaveBeenCalled();
    expect(component.form.touched).toBeTrue();
  });

  it('submit deve navegar para a home da role apos login com sucesso', () => {
    mockAuth.login.and.returnValue(of(void 0));
    mockAuth.getRole.and.returnValue('CLIENTE');
    const component = criarComponente();
    component.form.setValue({ cpfCnpj: '529.982.247-25', senha: '123456' });

    component.submit();

    expect(mockAuth.login).toHaveBeenCalledWith('529.982.247-25', '123456');
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/minha-conta/minhas-ordens']);
  });

  it('submit deve navegar para /dashboard quando a role nao esta mapeada', () => {
    mockAuth.login.and.returnValue(of(void 0));
    mockAuth.getRole.and.returnValue(null);
    const component = criarComponente();
    component.form.setValue({ cpfCnpj: '529.982.247-25', senha: '123456' });

    component.submit();

    expect(mockRouter.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('submit deve exibir mensagem de erro quando login falha', () => {
    mockAuth.login.and.returnValue(throwError(() => ({ status: 401 })));
    const component = criarComponente();
    component.form.setValue({ cpfCnpj: '529.982.247-25', senha: 'errada' });

    component.submit();

    expect(component.errorMsg()).toBe('CPF/CNPJ ou senha inválidos. Verifique suas credenciais.');
    expect(component.loading()).toBeFalse();
    expect(mockRouter.navigate).not.toHaveBeenCalled();
  });

});
