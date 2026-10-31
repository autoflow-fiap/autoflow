import unittest
from contextlib import redirect_stdout
from io import StringIO
from unittest.mock import patch

from smoke_api_gateway import check, is_lambda_proxy_uri, parse_cors_origins, response_diagnostics, smoke


class LambdaIntegrationUriTests(unittest.TestCase):
    def test_accepts_api_gateway_lambda_proxy_uri_for_expected_region_and_account(self):
        uri = (
            "arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/"
            "arn:aws:lambda:us-east-1:123456789012:function:autoflow-login/invocations"
        )
        self.assertTrue(is_lambda_proxy_uri(uri, "us-east-1", "123456789012"))

    def test_rejects_lambda_uri_for_different_account_or_region(self):
        uri = (
            "arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/"
            "arn:aws:lambda:us-east-1:123456789012:function:autoflow-login/invocations"
        )
        self.assertFalse(is_lambda_proxy_uri(uri, "us-west-2", "123456789012"))
        self.assertFalse(is_lambda_proxy_uri(uri, "us-east-1", "999999999999"))

    def test_rejects_non_invocation_uri(self):
        uri = "arn:aws:lambda:us-east-1:123456789012:function:autoflow-login"
        self.assertFalse(is_lambda_proxy_uri(uri, "us-east-1", "123456789012"))


class CorsOriginsTests(unittest.TestCase):
    def test_empty_array_has_no_authorized_origins(self):
        self.assertEqual(parse_cors_origins("[]"), [])

    def test_reads_each_configured_origin(self):
        origins = ["https://one.example", "https://two.example"]
        self.assertEqual(parse_cors_origins('["https://one.example", "https://two.example"]'), origins)

    def test_rejects_invalid_json(self):
        with self.assertRaisesRegex(ValueError, "array JSON válido"):
            parse_cors_origins("[invalid]")

    def test_rejects_non_array_json(self):
        with self.assertRaisesRegex(ValueError, "array JSON"):
            parse_cors_origins('"https://one.example"')

    def test_non_authorized_origin_check_remains_with_empty_list(self):
        def respond(base, path, method="GET", body=None, headers=None):
            if path == "/auth/login" and method == "POST" and body == {"cpf_cnpj": "52998224725", "senha": "senha-teste-invalida"}:
                return 401, {"Content-Type": "application/json"}, '{"message": "CPF/CNPJ ou senha inválidos."}'.encode()
            if path == "/auth/login" and method == "OPTIONS":
                return 204, {"Access-Control-Allow-Origin": "https://allowed.example"}, b""
            if path == "/auth/login" and method == "POST":
                return 400, {}, b""
            if path == "/auth/login":
                return 404, {}, b""
            if path in ("/api/clientes", "/api/veiculos", "/api/ordens-servico", "/api/orcamentos"):
                return (401 if not headers else 403), {}, b""
            if path in ("/", "/login", "/ordens-servico/nova", "/minha-conta/minhas-ordens", "/public/acompanhamento"):
                return 200, {}, b'<app-root><script src="main.js"></script></app-root>'
            if path == "/main.js":
                return 200, {}, b"bundle"
            return 404, {}, b""

        with patch("smoke_api_gateway.request", side_effect=respond) as request:
            smoke("https://api.example", [])
        options = [call for call in request.call_args_list if len(call.args) > 2 and call.args[2] == "OPTIONS"]
        self.assertEqual(len(options), 1)
        self.assertEqual(options[0].kwargs["headers"]["Origin"], "https://origem-nao-autorizada.invalid")


class HttpResponseDiagnosticsTests(unittest.TestCase):
    def test_failed_check_reports_status_and_safe_metadata_without_response_data(self):
        sensitive_body = (
            b'{"token":"token-sentinel","cpf_cnpj":"document-sentinel",'
            b'"senha":"password-sentinel","cliente":"customer-sentinel"}'
        )
        headers = {
            "Content-Type": "application/problem+json; charset=utf-8",
            "x-amzn-requestid": "request-id-123",
            "x-amz-apigw-id": "gateway-id-456",
            "x-amzn-errortype": "ForbiddenException",
            "Authorization": "Bearer token-sentinel",
            "Set-Cookie": "session=customer-sentinel",
        }
        diagnostics = response_diagnostics(403, headers, sensitive_body, expected_status=200)

        with self.assertRaises(RuntimeError) as raised:
            check(False, "consulta autenticada", diagnostics)

        message = str(raised.exception)
        self.assertIn("HTTP 403", message)
        self.assertIn("esperado HTTP 200", message)
        self.assertIn("content-type=application/problem+json", message)
        self.assertIn(f"response-bytes={len(sensitive_body)}", message)
        self.assertIn("apigw-request-id=request-id-123", message)
        self.assertIn("apigw-id=gateway-id-456", message)
        self.assertIn("aws-error-type=ForbiddenException", message)
        for sensitive_value in ("token-sentinel", "document-sentinel", "password-sentinel", "customer-sentinel"):
            self.assertNotIn(sensitive_value, message)

    def test_success_check_logs_the_effective_http_status(self):
        diagnostics = response_diagnostics(200, {"Content-Type": "application/json"}, b"[]", expected_status=200)
        output = StringIO()

        with redirect_stdout(output):
            check(True, "consulta autenticada", diagnostics)

        self.assertIn("HTTP 200", output.getvalue())
        self.assertIn("response-bytes=2", output.getvalue())


if __name__ == "__main__":
    unittest.main()
