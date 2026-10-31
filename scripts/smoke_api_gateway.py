#!/usr/bin/env python3
"""Homologa HTTP API sem escrever dados de negócio ou imprimir tokens/respostas."""
import argparse
import json
import os
import re
import subprocess
import urllib.error
import urllib.parse
import urllib.request


def request(base, path, method="GET", body=None, headers=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(base + path, data=data, method=method,
                                 headers={"Content-Type": "application/json", **(headers or {})})
    try:
        response = urllib.request.urlopen(req, timeout=35)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, response.headers, response.read()


def check(condition, label, diagnostics=None):
    if not condition:
        suffix = " — " + diagnostics if diagnostics else ""
        raise RuntimeError("FALHOU: " + label + suffix)
    suffix = " — " + diagnostics if diagnostics else ""
    print("OK: " + label + suffix)


def response_diagnostics(status, headers, body, expected_status):
    """Retorna metadados seguros da resposta, sem registrar conteúdo ou headers arbitrários."""
    details = [f"HTTP {status}", f"esperado HTTP {expected_status}"]
    header_values = {str(name).lower(): str(value) for name, value in headers.items()}

    content_type = header_values.get("content-type", "").split(";", 1)[0].strip()
    if re.fullmatch(r"[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+", content_type):
        details.append("content-type=" + content_type)

    details.append(f"response-bytes={len(body)}")
    safe_headers = {
        "x-amzn-requestid": "apigw-request-id",
        "x-amz-apigw-id": "apigw-id",
        "x-amzn-errortype": "aws-error-type",
    }
    for header, label in safe_headers.items():
        value = header_values.get(header, "").strip()
        if re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._:/=+-]{0,127}", value):
            details.append(f"{label}={value}")

    return "; ".join(details)


def parse_cors_origins(value):
    try:
        origins = json.loads(value)
    except (TypeError, json.JSONDecodeError) as error:
        raise ValueError("API_GATEWAY_CORS_ORIGINS deve ser um array JSON válido") from error
    if not isinstance(origins, list) or any(not isinstance(origin, str) or not origin.strip() for origin in origins):
        raise ValueError("API_GATEWAY_CORS_ORIGINS deve ser um array JSON de origens não vazias")
    return origins


def aws(*args):
    result = subprocess.run(["aws", *args, "--output", "json"], capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError("Consulta AWS falhou; verifique sessão e permissões: " + " ".join(args[:2]))
    return json.loads(result.stdout)


def is_lambda_proxy_uri(uri, region, account):
    prefix = (
        f"arn:aws:apigateway:{region}:lambda:path/2015-03-31/functions/"
        f"arn:aws:lambda:{region}:{account}:function:"
    )
    return isinstance(uri, str) and uri.startswith(prefix) and uri.endswith("/invocations")


def infrastructure(api_id, region, account):
    check(aws("sts", "get-caller-identity")["Account"] == account, "conta AWS esperada")
    integrations = aws("apigatewayv2", "get-integrations", "--api-id", api_id, "--region", region)["Items"]
    routes = aws("apigatewayv2", "get-routes", "--api-id", api_id, "--region", region)["Items"]
    login = next(route for route in routes if route["RouteKey"] == "POST /auth/login")
    integration = next(item for item in integrations if item["IntegrationId"] == login["Target"].split("/")[1])
    check(integration["IntegrationType"] == "AWS_PROXY" and integration["PayloadFormatVersion"] == "2.0"
          and is_lambda_proxy_uri(integration["IntegrationUri"], region, account),
          "login usa integração Lambda nativa v2")
    for link_id in {item["ConnectionId"] for item in integrations if item.get("ConnectionType") == "VPC_LINK"}:
        link = aws("apigatewayv2", "get-vpc-link", "--vpc-link-id", link_id, "--region", region)
        check(link["VpcLinkStatus"] == "AVAILABLE", "VPC Link AVAILABLE")
    for listener_arn in {item["IntegrationUri"] for item in integrations if item.get("ConnectionType") == "VPC_LINK"}:
        listener = aws("elbv2", "describe-listeners", "--listener-arns", listener_arn, "--region", region)["Listeners"][0]
        lb = aws("elbv2", "describe-load-balancers", "--load-balancer-arns", listener["LoadBalancerArn"], "--region", region)["LoadBalancers"][0]
        check(lb["Scheme"] == "internal", "balanceador interno")
        for action in listener["DefaultActions"]:
            if "TargetGroupArn" in action:
                targets = aws("elbv2", "describe-target-health", "--target-group-arn", action["TargetGroupArn"], "--region", region)["TargetHealthDescriptions"]
                check(len(targets) >= 2 and all(target["TargetHealth"]["State"] == "healthy" for target in targets), "targets saudáveis nos nós EKS")
    if os.getenv("RDS_INSTANCE_ID"):
        db = aws("rds", "describe-db-instances", "--db-instance-identifier", os.environ["RDS_INSTANCE_ID"], "--region", region)["DBInstances"][0]
        check(not db["PubliclyAccessible"] and db["StorageEncrypted"], "RDS privado e criptografado")


def smoke(base, cors_origins=None, login_only=False):
    check(base.startswith("https://") and not base.endswith("/"), "URL HTTPS sem barra final")
    status, headers, body = request(base, "/auth/login", "POST", {"cpf_cnpj": "52998224725", "senha": "senha-teste-invalida"})
    check(status == 401 and json.loads(body) == {"message": "CPF/CNPJ ou senha inválidos."}, "login inválido consulta Lambda/RDS e retorna 401 sanitizado")
    check("json" in headers.get("Content-Type", ""), "login retorna JSON")
    for payload in ({}, {"cpf": "52998224725", "senha": "invalida"}, []):
        check(request(base, "/auth/login", "POST", payload)[0] == 400, "payload inválido retorna 400")
    for method in ("GET", "PUT", "DELETE", "PATCH"):
        check(request(base, "/auth/login", method)[0] == 404, "método sem rota bloqueado: " + method)
    for path in ("/api/auth/login", "/api/inexistente", "/api/clientes/1/nao-existe", "/actuator", "/actuator/health", "/api/actuator/health", "/swagger-ui/index.html", "/rota-inexistente"):
        if login_only and path in ("/api/clientes/1/nao-existe", "/actuator", "/actuator/health", "/api/actuator/health", "/swagger-ui/index.html", "/rota-inexistente"):
            continue
        check(request(base, path)[0] == 404, "rota reservada/desconhecida bloqueada: " + path)
    if login_only:
        print("PENDENTE: frontend e backend dependem do Apply Kubernetes aprovado")
        return
    for path in ("/api/clientes", "/api/veiculos", "/api/ordens-servico", "/api/orcamentos"):
        check(request(base, path)[0] in (401, 403), "JWT obrigatório: " + path)
        check(request(base, path, headers={"Authorization": "Bearer token-ficticio-invalido"})[0] in (401, 403), "JWT inválido recusado: " + path)
    for path in ("/", "/login", "/ordens-servico/nova", "/minha-conta/minhas-ordens", "/public/acompanhamento"):
        status, headers, body = request(base, path)
        check(status == 200 and b"<app-root" in body, "SPA e deep link: " + path)
    status, _, index = request(base, "/")
    assets = re.findall(r'(?:src|href)="([^"?#]+\.(?:js|css))"', index.decode())
    check(bool(assets), "index referencia bundles")
    for asset in assets:
        check(not urllib.parse.urlsplit(asset).netloc, "bundle same-origin")
        status, headers, data = request(base, "/" + asset.lstrip("/"))
        check(status == 200 and b"<app-root" not in data, "bundle servido pelo Nginx: " + asset)
    for cors_origin in cors_origins or []:
        status, headers, _ = request(base, "/auth/login", "OPTIONS", headers={"Origin": cors_origin, "Access-Control-Request-Method": "POST", "Access-Control-Request-Headers": "content-type"})
        check(status in (200, 204) and headers.get("Access-Control-Allow-Origin") == cors_origin, "preflight para origem autorizada: " + cors_origin)
    _, headers, _ = request(base, "/auth/login", "OPTIONS", headers={"Origin": "https://origem-nao-autorizada.invalid", "Access-Control-Request-Method": "POST"})
    check(headers.get("Access-Control-Allow-Origin") not in ("*", "https://origem-nao-autorizada.invalid"), "origem externa não autorizada sem CORS")
    document = os.getenv("AUTOFLOW_TEST_CPF_CNPJ")
    password = os.getenv("AUTOFLOW_TEST_PASSWORD")
    if document and password:
        status, _, body = request(base, "/auth/login", "POST", {"cpf_cnpj": document, "senha": password})
        check(status == 200, "login de cliente fictício ativo via Lambda")
        token = json.loads(body)["token"]
        status, headers, body = request(
            base,
            "/api/clientes/me/ordens-servico",
            headers={"Authorization": "Bearer " + token},
        )
        check(status == 200, "GET /api/clientes/me/ordens-servico com JWT de cliente fictício",
              response_diagnostics(status, headers, body, expected_status=200))
    else:
        print("PENDENTE: login válido e consulta exigem AUTOFLOW_TEST_CPF_CNPJ e AUTOFLOW_TEST_PASSWORD de cliente fictício ATIVO")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base")
    parser.add_argument("--api-id")
    parser.add_argument("--region", default=os.getenv("AWS_REGION", "us-east-1"))
    parser.add_argument("--account", default=os.getenv("AWS_EXPECTED_ACCOUNT_ID"))
    parser.add_argument("--cors-origins", default=os.getenv("API_GATEWAY_CORS_ORIGINS", "[]"))
    parser.add_argument("--login-only", action="store_true", help="Homologa somente rotas de autenticação antes do Apply Kubernetes.")
    parser.add_argument("--require-authenticated", action="store_true")
    args = parser.parse_args()
    if args.require_authenticated and not (os.getenv("AUTOFLOW_TEST_CPF_CNPJ") and os.getenv("AUTOFLOW_TEST_PASSWORD")):
        parser.error("Homologação final exige credenciais de cliente fictício ATIVO nas variáveis de ambiente")
    if args.api_id:
        if not args.account:
            parser.error("--account é obrigatório para consultar infraestrutura")
        infrastructure(args.api_id, args.region, args.account)
    try:
        cors_origins = parse_cors_origins(args.cors_origins)
    except ValueError as error:
        parser.error(str(error))
    smoke(args.base, cors_origins, args.login_only)
