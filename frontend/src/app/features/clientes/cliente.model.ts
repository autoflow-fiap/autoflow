import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export interface ClienteRequest {
  nome: string;
  cpfCnpj: string;
  telefone: string;
  email: string;
}

export type ClienteStatus = 'ATIVO' | 'INATIVO';

export interface VeiculoCliente {
  id: number;
  marca: string;
  ano: number;
  placa: string;
  modelo: string;
}

export interface ClienteResponse {
  id: number;
  nome: string;
  cpfCnpj: string;
  telefone: string;
  email: string;
  status: ClienteStatus;
  veiculos: VeiculoCliente[];
}

export function formatarCpfCnpj(valor: string): string {
  const d = (valor ?? '').replace(/\D/g, '').slice(0, 14);
  if (d.length <= 11)
    return d
      .replace(/(\d{3})(\d)/, '$1.$2')
      .replace(/(\d{3})(\d)/, '$1.$2')
      .replace(/(\d{3})(\d{1,2})$/, '$1-$2');
  return d
    .replace(/(\d{2})(\d)/, '$1.$2')
    .replace(/(\d{3})(\d)/, '$1.$2')
    .replace(/(\d{3})(\d)/, '$1/$2')
    .replace(/(\d{4})(\d{1,2})$/, '$1-$2');
}

export function formatarTelefone(valor: string): string {
  const d = (valor ?? '').replace(/\D/g, '').slice(0, 11);
  if (!d.length) return '';
  if (d.length <= 10)
    return d
      .replace(/(\d{2})(\d)/, '($1) $2')
      .replace(/(\d{4})(\d{1,4})$/, '$1-$2');
  return d
    .replace(/(\d{2})(\d)/, '($1) $2')
    .replace(/(\d{5})(\d{1,4})$/, '$1-$2');
}

function validarCpf(cpf: string): boolean {
  if (/^(\d)\1{10}$/.test(cpf)) return false;
  let soma = 0;
  for (let i = 0; i < 9; i++) soma += +cpf[i] * (10 - i);
  let d1 = 11 - (soma % 11);
  if (d1 >= 10) d1 = 0;
  if (d1 !== +cpf[9]) return false;
  soma = 0;
  for (let i = 0; i < 10; i++) soma += +cpf[i] * (11 - i);
  let d2 = 11 - (soma % 11);
  if (d2 >= 10) d2 = 0;
  return d2 === +cpf[10];
}

function validarCnpj(cnpj: string): boolean {
  if (/^(\d)\1{13}$/.test(cnpj)) return false;
  let soma = 0, peso = 2;
  for (let i = 11; i >= 0; i--) {
    soma += +cnpj[i] * peso;
    peso = peso === 9 ? 2 : peso + 1;
  }
  const d1 = soma % 11 < 2 ? 0 : 11 - (soma % 11);
  if (d1 !== +cnpj[12]) return false;
  soma = 0; peso = 2;
  for (let i = 12; i >= 0; i--) {
    soma += +cnpj[i] * peso;
    peso = peso === 9 ? 2 : peso + 1;
  }
  const d2 = soma % 11 < 2 ? 0 : 11 - (soma % 11);
  return d2 === +cnpj[13];
}

export function cpfCnpjValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const raw = (control.value ?? '').replace(/\D/g, '');
    if (!raw) return null;
    if (raw.length === 11) return validarCpf(raw) ? null : { cpfCnpj: 'CPF inválido' };
    if (raw.length === 14) return validarCnpj(raw) ? null : { cpfCnpj: 'CNPJ inválido' };
    return { cpfCnpj: 'CPF deve ter 11 dígitos ou CNPJ 14 dígitos' };
  };
}
