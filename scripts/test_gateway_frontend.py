"""Integração local Nginx/artefatos Angular; Docker e npm run build são pré-requisitos."""
import json
from pathlib import Path
import subprocess
import time
import unittest
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]


class GatewayFrontendTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dist = ROOT / "frontend/dist/frontend/browser"
        if not (cls.dist / "index.html").exists():
            raise RuntimeError("Execute npm run build em frontend antes deste teste")
        cls.name = "autoflow-gateway-test-" + uuid.uuid4().hex[:8]
        subprocess.run([
            "docker", "run", "--rm", "-d", "--name", cls.name, "-p", "127.0.0.1::8080",
            "--mount", f"type=bind,source={cls.dist},target=/usr/share/nginx/html,readonly",
            "--mount", f"type=bind,source={ROOT / 'k8s/api-gateway/nginx.conf'},target=/etc/nginx/templates/default.conf.template,readonly",
            "nginxinc/nginx-unprivileged:1.27-alpine"
        ], check=True, capture_output=True)
        cls.addClassCleanup(lambda: subprocess.run(["docker", "stop", cls.name], check=True, capture_output=True))
        info = json.loads(subprocess.check_output(["docker", "inspect", cls.name]))[0]
        cls.base = "http://127.0.0.1:" + info["NetworkSettings"]["Ports"]["8080/tcp"][0]["HostPort"]
        for _ in range(30):
            try:
                if cls.get("/")[0] == 200:
                    return
            except (OSError, urllib.error.URLError):
                pass
            time.sleep(0.2)
        raise RuntimeError("Nginx não ficou disponível")

    @classmethod
    def get(cls, path):
        try:
            response = urllib.request.urlopen(cls.base + path, timeout=5)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            return response.status, response.read()

    def test_spa_deep_links(self):
        for path in ("/", "/login", "/ordens-servico/nova", "/ordens-servico/123", "/public/orcamentos/123", "/minha-conta/minhas-ordens/123"):
            with self.subTest(path=path):
                status, body = self.get(path)
                self.assertEqual(200, status)
                self.assertIn(b"<app-root", body)

    def test_nao_fazer_fallback_para_apis_ou_rotas_desconhecidas(self):
        for path in ("/api", "/api/clientes", "/auth/login", "/actuator", "/actuator/health", "/swagger-ui/index.html", "/nao-existe", "/assets/nao-existe.js"):
            with self.subTest(path=path):
                self.assertEqual(404, self.get(path)[0])

    def test_artefatos_binarios_e_textuais_preservados(self):
        assets = [path for path in self.dist.rglob("*") if path.is_file() and path.suffix in (".js", ".css", ".woff2", ".png", ".svg", ".ico")]
        self.assertTrue(assets)
        for asset in assets:
            with self.subTest(asset=asset.name):
                self.assertLess(asset.stat().st_size, 10 * 1024 * 1024, "Limite HTTP API: 10 MB")
                status, data = self.get("/" + asset.relative_to(self.dist).as_posix())
                self.assertEqual(200, status)
                self.assertEqual(asset.read_bytes(), data)


if __name__ == "__main__":
    unittest.main()
