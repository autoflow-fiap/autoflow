terraform {
  required_providers {
    kubectl = {
      source  = "gavinbunney/kubectl"
      version = ">= 1.7.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = ">= 2.0.0"
    }

  }
}


provider "aws" {
  region              = var.region_default
  allowed_account_ids = var.aws_allowed_account_ids

}

variable "aws_allowed_account_ids" {
  description = "Contas permitidas; o pipeline fornece obrigatoriamente a conta esperada."
  type        = list(string)

  validation {
    condition     = length(var.aws_allowed_account_ids) > 0 && alltrue([for id in var.aws_allowed_account_ids : can(regex("^[0-9]{12}$", id))])
    error_message = "aws_allowed_account_ids deve conter pelo menos um ID de conta AWS válido, fornecido externamente."
  }
}

provider "kubectl" {
  host                   = data.aws_eks_cluster.autoflow.endpoint
  cluster_ca_certificate = base64decode(data.aws_eks_cluster.autoflow.certificate_authority[0].data)
  token                  = data.aws_eks_cluster_auth.auth.token
  load_config_file       = false
}

provider "kubernetes" {
  host                   = data.aws_eks_cluster.autoflow.endpoint
  cluster_ca_certificate = base64decode(data.aws_eks_cluster.autoflow.certificate_authority[0].data)
  token                  = data.aws_eks_cluster_auth.auth.token

}
