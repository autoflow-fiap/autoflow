resource "kubectl_manifest" "backend_deployment" {
  yaml_body = templatefile("${path.module}/../k8s/backend-deployment.yaml", {
    dockerhub_username    = var.dockerhub_username
    image_tag             = var.backend_image_tag
    gateway_origin_hash   = sha256(jsonencode(local.gateway_config))
    newrelic_apm_required = var.enable_newrelic_apm
  })
  # As réplicas passam a ser controladas pelo HPA após a criação do Deployment.
  ignore_fields = ["spec.replicas"]
  depends_on    = [kubectl_manifest.autoflow_config]
}

resource "kubectl_manifest" "backend_hpa" {
  yaml_body  = file("${path.module}/../k8s/hpa.yaml")
  depends_on = [kubectl_manifest.backend_deployment]
}

resource "kubectl_manifest" "frontend_deployment" {
  yaml_body = templatefile("${path.module}/../k8s/frontend-deployment.yaml", {
    dockerhub_username = var.dockerhub_username
    image_tag          = var.frontend_image_tag
    gateway_nginx_hash = filesha256("${path.module}/../k8s/api-gateway/nginx.conf")
  })

  depends_on = [kubectl_manifest.autoflow_config, kubectl_manifest.gateway_nginx]
}
