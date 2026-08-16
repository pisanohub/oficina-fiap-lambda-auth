provider "aws" {
  region  = var.aws_region
  profile = var.aws_profile

  default_tags {
    tags = {
      Projeto     = "oficina-fiap"
      Componente  = "lambda-auth"
      Gerenciado  = "terraform"
      Repositorio = "oficina-fiap-lambda-auth"
    }
  }
}

