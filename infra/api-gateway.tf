variable "cluster_name" {
  type    = string
  default = "eks-autoflow"
}

variable "gateway_public_origin" {
  description = "Origem HTTPS do API Gateway, sem barra final; usada em CORS e links públicos da aplicação."
  type        = string
  validation {
    condition     = can(regex("^https://[a-zA-Z0-9.-]+$", var.gateway_public_origin))
    error_message = "gateway_public_origin deve ser HTTPS sem path."
  }
}

variable "gateway_cors_origins" {
  description = "Mesma allowlist cors_origins do gateway, incluindo origem HTTPS de coexistência quando necessária."
  type        = set(string)
  default     = []
  validation {
    condition     = alltrue([for origin in var.gateway_cors_origins : can(regex("^https://[a-zA-Z0-9.-]+(:[0-9]+)?$", origin))])
    error_message = "Informe somente origens HTTPS exatas."
  }
}

resource "kubectl_manifest" "gateway_nodeport" {
  for_each = {
    backend  = { node_port = 30081, target_port = 8081 }
    frontend = { node_port = 30080, target_port = 8080 }
  }
  yaml_body = yamlencode({
    apiVersion = "v1"
    kind       = "Service"
    metadata   = { name = "autoflow-${each.key}-gateway", namespace = "default" }
    spec = {
      type                  = "NodePort"
      externalTrafficPolicy = "Cluster"
      selector              = { app = "autoflow-${each.key}" }
      ports                 = [{ name = "http", port = 80, targetPort = each.value.target_port, nodePort = each.value.node_port, protocol = "TCP" }]
    }
  })
}

resource "kubectl_manifest" "gateway_nginx" {
  count = 1
  yaml_body = yamlencode({
    apiVersion = "v1"
    kind       = "ConfigMap"
    metadata   = { name = "autoflow-gateway-nginx", namespace = "default" }
    data       = { "default.conf.template" = file("${path.module}/../k8s/api-gateway/nginx.conf") }
  })
}
