import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ClienteRequest, ClienteResponse, ClienteStatus } from './cliente.model';

@Injectable({ providedIn: 'root' })
export class ClienteService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/clientes`;

  meuPerfil(): Observable<ClienteResponse> {
    return this.http.get<ClienteResponse>(`${this.base}/me`);
  }

  listarTodos(): Observable<ClienteResponse[]> {
    return this.http.get<ClienteResponse[]>(this.base);
  }

  buscarPorId(id: number): Observable<ClienteResponse> {
    return this.http.get<ClienteResponse>(`${this.base}/${id}`);
  }

  buscarPorDocumento(documento: string): Observable<ClienteResponse> {
    return this.http.get<ClienteResponse>(`${this.base}/${documento}`);
  }

  cadastrar(req: ClienteRequest): Observable<ClienteResponse> {
    return this.http.post<ClienteResponse>(this.base, req);
  }

  atualizar(id: number, req: ClienteRequest): Observable<ClienteResponse> {
    return this.http.patch<ClienteResponse>(`${this.base}/${id}/atualizacao`, req);
  }

  alterarStatus(id: number, status: ClienteStatus): Observable<ClienteResponse> {
    return this.http.patch<ClienteResponse>(`${this.base}/${id}/status`, { status });
  }

  // Backend retorna plain text, não JSON
  deletar(id: number): Observable<string> {
    return this.http.delete(`${this.base}/${id}`, { responseType: 'text' });
  }
}
