resource "kubectl_manifest" "backend_service" {
  yaml_body = file("${path.module}/../k8s/backend-service-internal.yaml")
}

resource "kubectl_manifest" "frontend_service" {
  yaml_body = file("${path.module}/../k8s/frontend-service-internal.yaml")
}

