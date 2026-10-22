
data "aws_eks_cluster" "autoflow" {
  name = var.cluster_name
}

data "aws_eks_cluster_auth" "auth" {
  name = var.cluster_name
}

data "terraform_remote_state" "database" {
  backend = "s3"

  config = {
    bucket = var.terraform_state_bucket
    key    = var.rds_state_key
    region = var.region_default
  }
}
