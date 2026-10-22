import unittest
from unittest.mock import patch

from smoke_api_gateway import parse_cors_origins, smoke


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


if __name__ == "__main__":
    unittest.main()
