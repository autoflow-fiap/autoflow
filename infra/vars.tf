variable "project_name" {
  default = "autoflow"
}

variable "region_default" {
  default = "us-east-1"
}

variable "terraform_state_bucket" {
  description = "Bucket externo que contém o state Terraform do RDS no ambiente Learner Lab."
  type        = string
}

variable "rds_state_key" {
  description = "Chave do state Terraform do RDS no bucket externo."
  type        = string
}

variable "cidr_vpc" {
  default = "10.0.0.0/16"
}

variable "instance_type" {
  default = "t3.medium"
}

variable "dockerhub_username" {
  description = "Namespace do Docker Hub fornecido pelo ambiente de deploy."
  type        = string
}

variable "backend_image_tag" {
  description = "Tag aprovada da imagem backend fornecida pelo ambiente de deploy."
  type        = string
}

variable "frontend_image_tag" {
  description = "Tag aprovada da imagem frontend fornecida pelo ambiente de deploy."
  type        = string
}

variable "enable_newrelic_apm" {
  description = "Controla se o processo da aplicação exige a License Key New Relic entregue fora do Terraform."
  type        = bool
  default     = false
}

variable "enable_http_request_logging" {
  description = "Habilita temporariamente logs HTTP seguros somente em homologação."
  type        = bool
  default     = false

  validation {
    condition     = !var.enable_http_request_logging || var.deployment_environment == "homolog"
    error_message = "Os logs HTTP de homologação não podem ser habilitados em produção."
  }
}

variable "deployment_environment" {
  description = "Ambiente selecionado pelo GitHub Actions; local é o fallback de desenvolvimento."
  type        = string
  default     = "local"
}
