locals {
  base_config = yamldecode(templatefile("${path.module}/../k8s/configmap.yaml", {
    rds_address = data.terraform_remote_state.database.outputs.postgres_endpoint
  }))
  gateway_config = {
    CORS_ALLOWED_ORIGINS     = join(",", sort(distinct(concat([var.gateway_public_origin], tolist(var.gateway_cors_origins)))))
    APP_PUBLIC_BASE_URL      = "${var.gateway_public_origin}/api"
    FRONTEND_PUBLIC_BASE_URL = var.gateway_public_origin
  }
}

resource "kubectl_manifest" "autoflow_config" {
  yaml_body = yamlencode(merge(local.base_config, {
    data = merge(local.base_config.data, local.gateway_config)
  }))

  depends_on = [kubectl_manifest.backend_service]
}

# O pipeline já gerencia esse Secret com scripts/apply_k8s_secret.py e valores
# protegidos. Remover apenas a gestão duplicada do state sem apagar o Secret.
removed {
  from = kubectl_manifest.autoflow_secrets

  lifecycle {
    destroy = false
  }
}
